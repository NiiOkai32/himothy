package com.campusfit.di
import android.content.Context
import androidx.room.Room
import com.campusfit.data.local.*
import com.campusfit.data.auth.AuthRepositoryImpl
import com.campusfit.domain.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): CampusDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, CampusDatabase::class.java, "campusfit.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(DatabaseKey.load(context))).build()
    }
    @Provides fun workoutClock(): com.campusfit.domain.workouts.WorkoutClock = com.campusfit.domain.workouts.WorkoutClock { android.os.SystemClock.elapsedRealtime() }
    @Provides @Singleton fun workouts(impl: com.campusfit.data.workouts.LocalWorkoutRepository): com.campusfit.domain.workouts.WorkoutRepository = impl
    @Provides @Singleton fun auth(impl: AuthRepositoryImpl): AuthRepository = impl
}
