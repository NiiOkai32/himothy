"""Execute production migration SQL on SQLite and compare exported Room schemas.
This does not exercise Android Room or SQLCipher runtime integration.
"""
from pathlib import Path
import json,re,sqlite3
root=Path(__file__).resolve().parents[1]
folder=root/'app/schemas/com.campusfit.data.local.CampusDatabase'
schema=lambda n:json.loads((folder/f'{n}.json').read_text())['database']
db=sqlite3.connect(':memory:')
for entity in schema(1)['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
db.execute("INSERT INTO profiles VALUES ('u','private-profile',1)")
db.execute("INSERT INTO weigh_ins VALUES ('u',20400,70.0,0,1)")
db.execute("INSERT INTO workout_sessions VALUES ('old-run','u',1,7,23.5,1)")
db.execute("INSERT INTO exercises VALUES ('old-exercise','Old','Core','Beginner',2.5,'[]','[]','old')")
source=(root/'app/src/main/java/com/campusfit/data/local/Migrations.kt').read_text()
for version in (2,3,4,5):
    block=source.split(f'val MIGRATION_{version-1}_{version} =')[1].split('val MIGRATION_')[0]
    for sql in re.findall(r'db\.execSQL\("([^"\n]+)"\)',block):db.execute(sql)
    for entity in schema(version)['entities']:
        columns={row[1]:row for row in db.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
        assert set(columns)=={f['columnName'] for f in entity['fields']},entity['tableName']
        for field in entity['fields']:
            column=columns[field['columnName']]
            assert column[2]==field['affinity'] and bool(column[3])==field['notNull'],field
            default=field.get('defaultValue');default=None if default=='undefined' else default
            assert column[4]==default,(column,field)
        assert {name for name,row in columns.items() if row[5]}==set(entity['primaryKey']['columnNames'])
    assert db.execute("SELECT profile FROM profiles WHERE uid='u'").fetchone()==('private-profile',)
    assert db.execute("SELECT weightKg FROM weigh_ins WHERE uid='u'").fetchone()==(70.0,)
    assert db.execute("SELECT durationSeconds,calories FROM workout_sessions WHERE id='old-run'").fetchone()==(420,23.5)
assert db.execute("SELECT createdAt FROM weigh_ins WHERE uid='u'").fetchone()==(20400*86400000,)
for _ in range(2):db.execute("INSERT OR IGNORE INTO workout_sessions (id,uid,startedAt,minutes,calories,syncPending,durationSeconds) VALUES ('new','u',1,0,1.5,1,20)")
assert db.execute("SELECT COUNT(*) FROM workout_sessions WHERE id='new'").fetchone()==(1,)
try:db.execute("INSERT INTO weigh_ins (uid,epochDay,weightKg,flagged,syncPending) VALUES ('u',20400,69,0,1)")
except sqlite3.IntegrityError:pass
else:raise AssertionError('A duplicate daily weigh-in was accepted')
db.execute("INSERT INTO outdoor_runs VALUES ('gps','u','{}',0)")
db.execute("INSERT INTO tracking_preferences VALUES ('u',1)")
db.execute("INSERT INTO account_deletions VALUES ('u')")
for table in ('outdoor_runs','tracking_preferences'):db.execute(f"DELETE FROM {table} WHERE uid='u'");assert db.execute(f'SELECT COUNT(*) FROM {table}').fetchone()==(0,)
assert db.execute("SELECT COUNT(*) FROM account_deletions WHERE uid='u'").fetchone()==(1,)
db.execute("INSERT INTO social_cache VALUES ('u','board:x','{}',1,1)")
db.execute("DELETE FROM social_cache WHERE uid='u'")
assert db.execute("SELECT COUNT(*) FROM social_cache").fetchone()==(0,)
dao=(root/'app/src/main/java/com/campusfit/data/local/CampusDatabase.kt').read_text()
query=re.search(r'@Query\("([^"\n]+)"\) suspend fun pendingSessions',dao).group(1)
db.execute("UPDATE workout_sessions SET syncIssue='Rejected: quota' WHERE id='old-run'")
assert 'old-run' not in {row[0] for row in db.execute(query,{'uid':'u'})}
assert 'new' in {row[0] for row in db.execute(query,{'uid':'u'})}
clear=re.search(r'@Query\("([^"\n]+)"\) suspend fun clearSessionIssues',dao).group(1)
db.execute(clear,{'uid':'u'})
assert 'old-run' in {row[0] for row in db.execute(query,{'uid':'u'})}
for _ in range(2):db.execute("INSERT OR IGNORE INTO mini_completions VALUES ('u',20400,'task',1)")
assert db.execute("SELECT COUNT(*) FROM mini_completions").fetchone()==(1,)
for uid in ('u','other'):
    db.execute("INSERT INTO engagement_preferences VALUES (?,0,0,0,'UTC',480,1200,1320,480,31,10,0,'')",(uid,))
    db.execute("INSERT INTO timetable_blocks VALUES (?,'class',1,2)",(uid,))
    db.execute("INSERT INTO notification_inbox VALUES (?,'note','test','notifications',1,0,0,0)",(uid,))
    db.execute("INSERT INTO seen_awards VALUES (?,'award')",(uid,))
    db.execute("INSERT INTO reminder_days VALUES (?,20400,1,0)",(uid,))
db.execute("INSERT INTO mini_completions VALUES ('other',20400,'task',1)")
engagement_dao=dao.split('@Dao interface EngagementDao')[1]
for table,method in [('mini_completions','deleteMinis'),('timetable_blocks','clearBlocks'),('notification_inbox','deleteInbox'),('seen_awards','deleteAwards'),('reminder_days','deleteDays'),('engagement_preferences','deletePreferences')]:
    query=re.search(r'@Query\("([^"\n]+)"\) suspend fun '+method,engagement_dao).group(1)
    db.execute(query,{'uid':'u'})
    assert db.execute(f"SELECT COUNT(*) FROM {table} WHERE uid='u'").fetchone()==(0,)
    assert db.execute(f"SELECT COUNT(*) FROM {table} WHERE uid='other'").fetchone()==(1,)
print('PASS: v1→v2→v3→v4→v5 schemas/defaults/keys and private data preserved; unique sessions/daily weights and local erasure checks passed')
