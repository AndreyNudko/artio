/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway;

import uk.co.real_logic.fix_gateway.streams.GatewayPublication;

import static uk.co.real_logic.fix_gateway.LivenessDetector.LivenessState.AWAITING_CONNECT;
import static uk.co.real_logic.fix_gateway.LivenessDetector.LivenessState.CONNECTED;
import static uk.co.real_logic.fix_gateway.LivenessDetector.LivenessState.DISCONNECTED;

/**
 * Bidirection application level liveness detector.
 *
 * For use between an engine and a library.
 */
public final class LivenessDetector
{
    public enum LivenessState
    {
        AWAITING_CONNECT,
        CONNECTED,
        DISCONNECTED
    }

    private final GatewayPublication publication;
    private final int libraryId;
    private final long replyTimeoutInMs;
    private final long sendIntervalInMs;

    private long latestNextReceiveTimeInMs;
    private long nextSendTimeInMs;
    private LivenessState state;

    public static LivenessDetector forEngine(
        final GatewayPublication publication,
        final int libraryId,
        final long replyTimeoutInMs,
        final long timeInMs)
    {
        final LivenessDetector detector = new LivenessDetector(publication, libraryId, replyTimeoutInMs, CONNECTED);
        detector.latestNextReceiveTimeInMs = timeInMs + replyTimeoutInMs;
        detector.heartbeat(timeInMs);
        return detector;
    }

    public static LivenessDetector forLibrary(
        final GatewayPublication publication,
        final int libraryId,
        final long replyTimeoutInMs)
    {
        return new LivenessDetector(publication, libraryId, replyTimeoutInMs, AWAITING_CONNECT);
    }

    private LivenessDetector(
        final GatewayPublication publication,
        final int libraryId,
        final long replyTimeoutInMs,
        final LivenessState state)
    {
        this.publication = publication;
        this.libraryId = libraryId;
        this.replyTimeoutInMs = replyTimeoutInMs;
        this.state = state;
        this.sendIntervalInMs = replyTimeoutInMs / 4;
    }

    public boolean isConnected()
    {
        return state == CONNECTED;
    }

    public int poll(final long timeInMs)
    {
        switch (state)
        {
            case CONNECTED:
                if (timeInMs > latestNextReceiveTimeInMs)
                {
                    state = DISCONNECTED;
                    return 1;
                }

            case AWAITING_CONNECT: // Fallthrough
                if (timeInMs > nextSendTimeInMs)
                {
                    heartbeat(timeInMs);
                    return 1;
                }
        }

        return 0;
    }

    public void onHeartbeat(final long timeInMs)
    {
        if (state != CONNECTED)
        {
            state = CONNECTED;
        }
        latestNextReceiveTimeInMs = timeInMs + replyTimeoutInMs;
    }

    public void heartbeat(final long timeInMs)
    {
        nextSendTimeInMs = timeInMs + sendIntervalInMs;
        publication.saveApplicationHeartbeat(libraryId);
    }
}