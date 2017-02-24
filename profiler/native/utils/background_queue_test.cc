/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "background_queue.h"

#include <gtest/gtest.h>
#include "utils/count_down_latch.h"

using profiler::BackgroundQueue;
using profiler::CountDownLatch;

TEST(BackgroundQueue, EnqueuingTasksWorks) {
  CountDownLatch job_1_waiting(1);
  CountDownLatch job_2_waiting(1);

  BackgroundQueue bq("BQTestThread");
  bq.EnqueueTask([&] { job_1_waiting.Await(); });
  bq.EnqueueTask([&] { job_2_waiting.Await(); });

  EXPECT_FALSE(bq.IsIdle());
  job_1_waiting.CountDown();

  EXPECT_FALSE(bq.IsIdle());
  job_2_waiting.CountDown();

  while (!bq.IsIdle()) {
    std::this_thread::yield();
  }
}

TEST(BackgroundQueue, DestructorBlocksUntilJobsFinish) {
  const int kNumJobs = 12345;
  CountDownLatch first_job_started(1);
  int num_jobs_run = 0;

  {
    BackgroundQueue bq("BQTestThread");
    bq.EnqueueTask([&] { first_job_started.Await(); });
    for (int i = 0; i < kNumJobs; ++i) {
      bq.EnqueueTask([&] { ++num_jobs_run; });
    }
    first_job_started.CountDown();
    EXPECT_NE(kNumJobs, num_jobs_run);
  }  // Blocked here until all enqueued tasks run
  EXPECT_EQ(kNumJobs, num_jobs_run);
}
