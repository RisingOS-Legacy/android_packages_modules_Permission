/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.INTERVAL_MONTHLY
import android.os.UserHandle
import android.os.UserManager
import com.android.permissioncontroller.PermissionControllerApplication
import kotlinx.coroutines.Job

/**
 * A livedata which tracks the usage stats for all packages for all users in a given length of time.
 *
 * @param app The current application
 * @param searchTimeMs The length of time, in milliseconds, that this LiveData will track. The time
 * will start when the liveData is loaded, and extend backwards searchTimeMs milliseconds.
 * @param interval The interval to measure in. Default is monthly.
 */
class UsageStatsLiveData private constructor(
    private val app: Application,
    private val searchTimeMs: Long,
    private val interval: Int = INTERVAL_MONTHLY
) : SmartAsyncMediatorLiveData<Map<UserHandle, List<UsageStats>>>() {

    init {
        addSource(UsersLiveData) {
            updateIfActive()
        }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (!UsersLiveData.isInitialized) {
            return
        }

        val now = System.currentTimeMillis()
        val userMap = mutableMapOf<UserHandle, List<UsageStats>>()
        val enabledUsers = app.getSystemService(UserManager::class.java)!!.enabledProfiles
        for (user in UsersLiveData.value!!) {
            if (user !in enabledUsers) {
                continue
            }
            userMap[user] = app.getSystemService(UsageStatsManager::class.java)!!.queryUsageStats(
            interval, now - searchTimeMs, now)
        }

        postValue(userMap)
    }

    override fun onActive() {
        super.onActive()
        updateIfActive()
    }

    companion object : DataRepository<Pair<Long, Int>, UsageStatsLiveData>() {
        override fun newValue(key: Pair<Long, Int>): UsageStatsLiveData {
            return UsageStatsLiveData(PermissionControllerApplication.get(), key.first, key.second)
        }

        operator fun get(interval: Long): UsageStatsLiveData {
            return get(interval to INTERVAL_MONTHLY)
        }
    }
}