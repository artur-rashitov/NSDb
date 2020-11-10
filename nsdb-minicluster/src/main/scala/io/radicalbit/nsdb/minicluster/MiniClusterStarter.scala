/*
 * Copyright 2018-2020 Radicalbit S.r.l.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.radicalbit.nsdb.minicluster

import java.time.Duration

object MiniClusterStarter extends App with NSDbMiniCluster {
  override protected[this] def nodesNumber              = 3
  override protected[this] def replicationFactor: Int   = 2
  override protected[this] def rootFolder: String       = s"target/minicluster/$instanceId"
  override protected[this] def shardInterval: Duration  = Duration.ofMillis(5)
  override protected[this] def passivateAfter: Duration = Duration.ofHours(1)

  start()
}
