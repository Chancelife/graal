/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.truffle.compiler.phases.PhiTransformPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;
import org.junit.Test;

public class PhiTransformTest extends GraalCompilerTest {

    public static float narrowPhiLoopSnippet(int count) {
        long[] values = new long[2];
        do {
            values[0] = ((int) values[0] + 1) & 0xffffffffL;
            values[1] = Float.floatToRawIntBits(Float.intBitsToFloat((int) values[1]) + 1) & 0xffffffffL;
        } while ((int) values[0] < count);
        return Float.intBitsToFloat((int) values[1]);
    }

    @Test
    public void narrowLoopPhiTest() {
        StructuredGraph graph = createGraph("narrowPhiLoopSnippet");
        Assert.assertEquals(0, graph.getNodes().filter(ReinterpretNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(NarrowNode.class).count());
        // one ZeroExtendNode remains for the loop proxy
        Assert.assertEquals(1, graph.getNodes().filter(ZeroExtendNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(SignExtendNode.class).count());
    }

    public static float succeed1Snippet(int count) {
        long[] values = new long[1];
        float s = 0;
        do {
            values[0] = ((int) values[0] + 1) & 0xffffffffL;
            s++;
        } while ((int) values[0] < count);
        return s;
    }

    @Test
    public void succeed1() {
        StructuredGraph graph = createGraph("succeed1Snippet");
        Assert.assertTrue(graph.getNodes().filter(ReinterpretNode.class).count() + graph.getNodes().filter(NarrowNode.class).count() == 0);
    }

    public static float succeed2Snippet(int count) {
        long[] values = new long[2];
        float s = 0;
        do {
            values[0] = ((int) values[0] + 1) & 0xffffffffL;
            values[1] = 0L;
            do {
                values[1] = ((int) values[1] + 1) & 0xffffffffL;
                s++;
            } while ((int) values[1] < count);
            s++;
        } while ((int) values[0] < count);
        return s;
    }

    @Test
    public void succeed2() {
        StructuredGraph graph = createGraph("succeed2Snippet");
        Assert.assertTrue(graph.getNodes().filter(ReinterpretNode.class).count() + graph.getNodes().filter(NarrowNode.class).count() == 0);
    }

    public static float fail1Snippet(int count) {
        long[] values = new long[1];
        float s = 0;
        do {
            values[0] = ((int) values[0] + 1) & 0x7fffffffL; // not masking to int exactly
            s++;
        } while ((int) values[0] < count);
        return s;
    }

    @Test
    public void fail1() {
        StructuredGraph graph = createGraph("fail1Snippet");
        Assert.assertTrue(graph.getNodes().filter(ReinterpretNode.class).count() + graph.getNodes().filter(NarrowNode.class).count() > 0);
    }

    public static volatile long zero;
    public static volatile long sink;

    public static float fail2Snippet(int count) {
        long[] values = new long[1];
        float s = 0;
        do {
            sink = values[0]; // non-narrowed value escapes
            values[0] = ((int) values[0] + 1) & 0xffffffffL;
            s++;
        } while ((int) values[0] < count);
        return s;
    }

    @Test
    public void fail2() {
        StructuredGraph graph = createGraph("fail2Snippet");
        Assert.assertTrue(graph.getNodes().filter(ReinterpretNode.class).count() + graph.getNodes().filter(NarrowNode.class).count() > 0);
    }

    public static float fail3Snippet(int count) {
        long[] values = new long[]{zero}; // non-constant starting value
        float s = 0;
        do {
            values[0] = ((int) values[0] + 1) & 0xffffffffL;
            s++;
        } while ((int) values[0] < count);
        return s;
    }

    @Test
    public void fail3() {
        StructuredGraph graph = createGraph("fail3Snippet");
        Assert.assertTrue(graph.getNodes().filter(ReinterpretNode.class).count() + graph.getNodes().filter(NarrowNode.class).count() > 0);
    }

    public static float fail4Snippet(int count) {
        long[] values = new long[]{0L};
        float s = 0;
        do {
            // mismatch double - float
            values[0] = Float.floatToRawIntBits((float) Double.longBitsToDouble(values[0]) + 1) & 0xffffffffL;
            s++;
        } while ((int) values[0] < count);
        return s;
    }

    @Test
    public void fail4() {
        StructuredGraph graph = createGraph("fail4Snippet");
        Assert.assertTrue(graph.getNodes().filter(ReinterpretNode.class).count() > 0);
        Assert.assertTrue(graph.getNodes().filter(ZeroExtendNode.class).count() > 0);
    }

    public static float fail5Snippet(int count) {
        long[] values = new long[]{0L};
        float s = 0;
        do {
            // int casts for double values
            values[0] = Double.doubleToRawLongBits(Double.longBitsToDouble((int) values[0]) + 1) & 0xffffffffL;
            s++;
        } while ((int) values[0] == count);
        return s;
    }

    @Test
    public void fail5() {
        StructuredGraph graph = createGraph("fail5Snippet");
        Assert.assertTrue(graph.getNodes().filter(ReinterpretNode.class).count() > 0);
        Assert.assertTrue(graph.getNodes().filter(NarrowNode.class).count() > 0);
    }

    private StructuredGraph createGraph(String name) {
        test(name, 100);
        StructuredGraph graph = parseEager(name, AllowAssumptions.YES);

        CoreProviders context = getProviders();
        createCanonicalizerPhase().apply(graph, context);
        new PartialEscapePhase(false, createCanonicalizerPhase(), getInitialOptions()).apply(graph, getDefaultHighTierContext());
        new PhiTransformPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        return graph;
    }
}
