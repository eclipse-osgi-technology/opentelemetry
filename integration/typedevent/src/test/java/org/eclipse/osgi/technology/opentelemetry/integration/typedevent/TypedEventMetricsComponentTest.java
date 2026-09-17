/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 * All rights reserved.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 */

package org.eclipse.osgi.technology.opentelemetry.integration.typedevent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Plain unit tests for the pure topic-prefix extraction logic in
 * {@link TypedEventMetricsComponent}, independent of any OSGi service
 * wiring. This test bundle is built as a fragment of the main
 * {@code typedevent} bundle (see the module's {@code pom.xml}) so it can
 * see the component's package-private {@code extractTopicPrefix} method.
 */
class TypedEventMetricsComponentTest {

    @Test
    void nullTopicYieldsUnknown() {
        assertThat(TypedEventMetricsComponent.extractTopicPrefix(null)).isEqualTo("unknown");
    }

    @Test
    void topicWithNoSlashesIsReturnedAsIs() {
        assertThat(TypedEventMetricsComponent.extractTopicPrefix("NoSlashesHere")).isEqualTo("NoSlashesHere");
    }

    @Test
    void emptyTopicIsReturnedAsIs() {
        assertThat(TypedEventMetricsComponent.extractTopicPrefix("")).isEqualTo("");
    }

    @Test
    void topicWithOnlyOneSlashIsReturnedAsIs() {
        assertThat(TypedEventMetricsComponent.extractTopicPrefix("org/foo")).isEqualTo("org/foo");
    }

    @Test
    void topicWithTwoSegmentsIsReturnedAsIs() {
        assertThat(TypedEventMetricsComponent.extractTopicPrefix("org/osgi")).isEqualTo("org/osgi");
    }

    @Test
    void topicWithMoreThanTwoSegmentsIsTruncatedToFirstTwo() {
        assertThat(TypedEventMetricsComponent.extractTopicPrefix("org/osgi/example/ExampleEvent")).isEqualTo("org/osgi");
    }

    @Test
    void topicWithManySegmentsStillTruncatesToFirstTwo() {
        assertThat(TypedEventMetricsComponent.extractTopicPrefix("a/b/c/d/e/f")).isEqualTo("a/b");
    }
}
