/*
 * Copyright 2013 Peter Lawrey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vanilla.java.stages;

import com.higherfrequencytrading.chronicle.Chronicle;
import com.higherfrequencytrading.chronicle.impl.IndexedChronicle;
import com.higherfrequencytrading.chronicle.tcp.InProcessChronicleSink;
import com.higherfrequencytrading.chronicle.tools.ChronicleTools;
import vanilla.java.stages.api.*;
import vanilla.java.stages.testing.Differencer;
import vanilla.java.stages.testing.VanillaDifferencer;

import java.io.IOException;
import java.util.Arrays;

/**
 * User: peter
 * Date: 05/08/13
 * Time: 17:33
 */
public class SinkMain {
    static final String HOST3 = System.getProperty("host3", "localhost");
    static final int PORT3 = Integer.getInteger("port3", SourceMain.PORT + 2);
    static final int MESSAGES = SourceMain.MESSAGES;
    static final String TMP = System.getProperty("java.io.tmpdir");

    public static void main(String... ignored) throws IOException, InterruptedException {
        String basePath3 = TMP + "/3-sink";

        ChronicleTools.deleteOnExit(basePath3);

        Chronicle chronicle3 = new IndexedChronicle(basePath3);
        InProcessChronicleSink sink3 = new InProcessChronicleSink(chronicle3, HOST3, PORT3);
        final EventsReader sinkReader = new EventsReader(sink3.createExcerpt(), new LatencyEvents(), TimingStage.SinkRead, TimingStage.EngineWrite);

        while (true) {
            if (!sinkReader.read())
                pause();
        }
    }

    private static void pause() {
        // nothing for now.
    }

    static class LatencyEvents implements Events {
        static final TimingStage[] VALUES = TimingStage.values();
        final long[][] timings = new long[VALUES.length - 1][SourceMain.MESSAGES];
        final Differencer[] differencers = {
                new VanillaDifferencer(), // same host
                new VanillaDifferencer(), // same host
//                new RunningMinimum(30 * 1000), // source to engine
                new VanillaDifferencer(), // same host
                new VanillaDifferencer(), // same host
                new VanillaDifferencer(), // same host
                new VanillaDifferencer(), // same host
//                new RunningMinimum(30 * 1000), // engine to sink
        };
        int count = 0;

        @Override
        public void onMarketData(MetaData metaData, Update update) {
            for (int i = 0; i < timings.length; i++) {
                long start = metaData.getTimeStamp(VALUES[i]);
                long end = metaData.getTimeStamp(VALUES[i + 1]);
                long delay = differencers[i].sample(start, end);
                timings[i][count] = delay;
            }
            if (count % 10000 == 0) {
                System.out.println(count);
                for (int i = 0; i < VALUES.length; i++) {
                    System.out.println(VALUES[i] + ": " + metaData.getTimeStamp(VALUES[i]));
                }
            }
            count++;

            if (count == MESSAGES) {
                System.out.printf("latencies\t50%%\t90%%\t99%%\t99.9%%\t99.99%%%n");
                for (int i = 0; i < timings.length; i++) {
                    Arrays.sort(timings[i]);
                    long t0 = timings[i][0];
                    long t50 = timings[i][count / 2];
                    long t90 = timings[i][count * 9 / 10];
                    long t99 = timings[i][count * 99 / 100];
                    long t99_9 = timings[i][count * 999 / 1000];
                    long t99_99 = timings[i][count - count / 10000];
                    switch (VALUES[i]) {
                        case SourceWrite:
                        case SinkWrite:
                            long correction = 30 * 1000 - t0;
                            t50 += correction;
                            t90 += correction;
                            t99 += correction;
                            t99_9 += correction;
                            t99_99 += correction;
                            break;
                    }
                    System.out.printf("%s-%s\t%s\t%s\t%s\t%s\t%s us%n",
                            VALUES[i].name(), VALUES[i + 1].name(),
                            t50 / 1e3,
                            t90 / 1e3,
                            t99 / 1e3,
                            t99_9 / 1e3,
                            t99_99 / 1e3
                    );
                }
                System.out.println();
                count = 0;
            }
        }
    }
}
