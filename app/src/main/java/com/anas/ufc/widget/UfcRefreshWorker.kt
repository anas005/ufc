package com.anas.ufc.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anas.ufc.data.MissingApiKeyException
import com.anas.ufc.data.MonthlyRequestBudgetException
import com.anas.ufc.data.RefreshConfig
import com.anas.ufc.data.RefreshStatus
import com.anas.ufc.data.UfcRepositoryProvider
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class UfcRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = UfcRepositoryProvider.get(applicationContext)
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val requestedEventSlug = inputData.getString(KEY_EVENT_SLUG)
        return try {
            repository.setRefreshStatus(RefreshStatus.REFRESHING)
            repository.refresh(manual, requestedEventSlug)
            updateWidget()
            Result.success()
        } catch (_: MissingApiKeyException) {
            repository.setRefreshStatus(RefreshStatus.API_KEY_REQUIRED)
            updateWidget()
            Result.failure()
        } catch (_: MonthlyRequestBudgetException) {
            repository.setRefreshStatus(RefreshStatus.MONTHLY_LIMIT_REACHED)
            updateWidget()
            Result.success()
        } catch (_: IOException) {
            repository.setRefreshStatus(RefreshStatus.OFFLINE)
            updateWidget()
            Result.retry()
        } catch (error: HttpException) {
            val retryable = error.code() == 429 || error.code() >= 500
            repository.setRefreshStatus(if (retryable) RefreshStatus.OFFLINE else RefreshStatus.ERROR)
            updateWidget()
            if (retryable) Result.retry() else Result.failure()
        } catch (_: SerializationException) {
            repository.setRefreshStatus(RefreshStatus.ERROR)
            updateWidget()
            Result.failure()
        }
    }

    private suspend fun updateWidget() {
        UfcWidget().updateAll(applicationContext)
    }

    companion object {
        private const val KEY_MANUAL = "manual"
        private const val KEY_EVENT_SLUG = "event_slug"
        private const val DAILY_WORK_NAME = "ufc_daily_refresh"
        private const val MANUAL_WORK_NAME = "ufc_manual_refresh"

        fun scheduleDaily(context: Context) {
            val delay = com.anas.ufc.data.nextScheduledRefreshMillis(ZonedDateTime.now())
            val request = PeriodicWorkRequestBuilder<UfcRefreshWorker>(
                RefreshConfig.automaticIntervalHours,
                TimeUnit.HOURS,
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraint())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RefreshConfig.workerBackoff.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueManual(context: Context) {
            val request = OneTimeWorkRequestBuilder<UfcRefreshWorker>()
                .setInputData(workDataOf(KEY_MANUAL to true))
                .setConstraints(networkConstraint())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RefreshConfig.workerBackoff.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueEvent(context: Context, slug: String) {
            val request = OneTimeWorkRequestBuilder<UfcRefreshWorker>()
                .setInputData(workDataOf(KEY_EVENT_SLUG to slug))
                .setConstraints(networkConstraint())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RefreshConfig.workerBackoff.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ufc_event_$slug",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private fun networkConstraint() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
