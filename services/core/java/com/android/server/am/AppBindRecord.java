/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server.am;

import android.util.ArraySet;
import android.util.proto.ProtoOutputStream;

import com.android.server.am.proto.AppBindRecordProto;

import java.io.PrintWriter;

/**
 * An association between a service and one of its client applications.
 */
final class AppBindRecord {
    final ServiceRecord service;    // The running service.
    final IntentBindRecord intent;  // The intent we are bound to.
    final ProcessRecord client;     // Who has started/bound the service.

    final ArraySet<ConnectionRecord> connections = new ArraySet<>();
                                    // All ConnectionRecord for this client.

    void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + "service=" + service);
        pw.println(prefix + "client=" + client);
        dumpInIntentBind(pw, prefix);
    }

    void dumpInIntentBind(PrintWriter pw, String prefix) {
        final int N = connections.size();
        if (N > 0) {
            pw.println(prefix + "Per-process Connections:");
            for (int i=0; i<N; i++) {
                ConnectionRecord c = connections.valueAt(i);
                pw.println(prefix + "  " + c);
            }
        }
    }

    AppBindRecord(ServiceRecord _service, IntentBindRecord _intent,
            ProcessRecord _client) {
        service = _service;
        intent = _intent;
        client = _client;
    }

    public String toString() {
        return "AppBindRecord{"
            + Integer.toHexString(System.identityHashCode(this))
            + " " + service.shortName + ":" + client.processName + "}";
    }

    void writeToProto(ProtoOutputStream proto, long fieldId) {
        long token = proto.start(fieldId);
        proto.write(AppBindRecordProto.HEX_HASH,
                Integer.toHexString(System.identityHashCode(this)));
        if (client != null) {
            client.writeToProto(proto, AppBindRecordProto.CLIENT);
        }
        final int N = connections.size();
        for (int i=0; i<N; i++) {
            connections.valueAt(i).writeToProto(proto, AppBindRecordProto.CONNECTIONS);
        }
        proto.end(token);
    }
}
