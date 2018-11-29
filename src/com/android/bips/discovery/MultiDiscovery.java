/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.discovery;

import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Combines the behavior of multiple child {@link Discovery} objects to a single one */
public class MultiDiscovery extends Discovery {
    private static final String TAG = MultiDiscovery.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final List<Discovery> mDiscoveries = new ArrayList<>();
    private final List<Discovery> mStartedDiscoveries = new ArrayList<>();
    private final Listener mChildListener;

    /**
     * Construct an aggregate discovery mechanism, with preferred discovery mechanisms first
     */
    public MultiDiscovery(Discovery first, Discovery... rest) {
        super(first.getPrintService());
        mDiscoveries.add(first);
        mDiscoveries.addAll(Arrays.asList(rest));
        mChildListener = new Listener() {
            @Override
            public void onPrinterFound(DiscoveredPrinter printer) {
                printerFound(merged(printer.getUri()));
            }

            @Override
            public void onPrinterLost(DiscoveredPrinter printer) {
                // Merge remaining printer records, if any
                DiscoveredPrinter remaining = merged(printer.getUri());
                if (remaining == null) {
                    printerLost(printer.getUri());
                } else {
                    printerFound(remaining);
                }
            }
        };
    }

    /**
     * For a given URI combine and return records with the same printerUri, based on discovery
     * mechanism order.
     */
    private DiscoveredPrinter merged(Uri printerUri) {
        DiscoveredPrinter combined = null;

        for (Discovery discovery : getChildren()) {
            DiscoveredPrinter discovered = discovery.getPrinter(printerUri);
            if (discovered != null) {
                if (combined == null) {
                    combined = discovered;
                } else {
                    // Merge all paths found, in order, without duplicates
                    List<Uri> allPaths = new ArrayList<>(combined.paths);
                    for (Uri path : discovered.paths) {
                        if (!allPaths.contains(path)) {
                            allPaths.add(path);
                        }
                    }
                    // Assemble a new printer containing paths.
                    combined = new DiscoveredPrinter(discovered.uuid, discovered.name, allPaths,
                            discovered.location);
                }
            }
        }

        return combined;
    }

    @Override
    void onStart() {
        if (DEBUG) Log.d(TAG, "onStart()");
        for (Discovery discovery : mDiscoveries) {
            discovery.start(mChildListener);
            mStartedDiscoveries.add(discovery);
        }
    }

    private void stopAndClearAll() {
        for (Discovery discovery : mStartedDiscoveries) {
            discovery.stop(mChildListener);
        }
        mStartedDiscoveries.clear();
        allPrintersLost();
    }

    @Override
    void onStop() {
        if (DEBUG) Log.d(TAG, "onStop()");
        stopAndClearAll();
    }

    @Override
    Collection<Discovery> getChildren() {
        List<Discovery> children = new ArrayList<>();
        for (Discovery child : mDiscoveries) {
            children.addAll(child.getChildren());
        }
        return children;
    }
}
