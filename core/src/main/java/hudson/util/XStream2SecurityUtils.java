/*
 * Copyright (C) 2021, 2022 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 *
 * Created on 21. September 2021 by Joerg Schaible
 */
// Updated when included in Jenkins code by changing currentTimeMillis to nanoTime + comments

package hudson.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.security.InputManipulationException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Strongly inspired by https://github.com/x-stream/xstream/blob/61a00fa225dc99488013869b57b772af8e2fea03/xstream/src/java/com/thoughtworks/xstream/core/SecurityUtils.java#L25
 * and taking into account https://github.com/x-stream/xstream/issues/282
 *
 * Once the related issue is fixed, we will be able to use the regular method from XStream.
 *
 * @see com.thoughtworks.xstream.core.SecurityUtils
 */
@Restricted(NoExternalUse.class)
public class XStream2SecurityUtils {
    /**
     * Check the consumed time adding elements to collections or maps.
     *
     * Every custom converter should call this method after an unmarshalled element has been added to a collection or
     * map. In case of an attack the operation will take too long, because the calculation of the hash code or the
     * comparison of the elements in the collection operate on recursive structures.
     *
     * @param context the unmarshalling context
     * @param startNano the nanoTime just before the element was added to the collection or map
     * @since 1.4.19
     */
    public static void checkForCollectionDoSAttack(final UnmarshallingContext context, final long startNano) {
        final int diff = (int) ((System.nanoTime() - startNano) / 1000_000_000);
        if (diff > 0) {
            final Integer secondsUsed = (Integer) context.get(XStream.COLLECTION_UPDATE_SECONDS);
            if (secondsUsed != null) {
                final Integer limit = (Integer) context.get(XStream.COLLECTION_UPDATE_LIMIT);
                if (limit == null) {
                    throw new ConversionException("Missing limit for updating collections.");
                }
                final int seconds = secondsUsed + diff;
                if (seconds > limit) {
                    throw new InputManipulationException(
                            "Denial of Service attack assumed. Adding elements to collections or maps exceeds " + limit + " seconds.");
                }
                context.put(XStream.COLLECTION_UPDATE_SECONDS, seconds);
            }
        }
    }
}
