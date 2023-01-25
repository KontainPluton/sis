/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.internal.filter;

import java.util.List;
import java.util.Collections;
import java.lang.reflect.Field;
import org.apache.sis.internal.filter.sqlmm.SQLMM;
import org.apache.sis.testutilities.TestCase;
import org.junit.Test;

import static org.junit.Assert.*;

import org.opengis.filter.Literal;
import org.opengis.filter.ValueReference;
import org.opengis.filter.ComparisonOperator;
import org.opengis.filter.ComparisonOperatorName;
import org.opengis.filter.BetweenComparisonOperator;
import org.opengis.filter.NullOperator;
import org.opengis.filter.NilOperator;
import org.opengis.filter.LikeOperator;
import org.opengis.filter.Expression;


/**
 * Verifies the values declared in {@link FunctionNames}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
public final strictfp class FunctionNamesTest extends TestCase {
    /**
     * Verifies that each field has the same name than its value.
     *
     * @throws IllegalAccessException should never happen.
     */
    @Test
    public void verifyFieldNames() throws IllegalAccessException {
        for (final Field f : FunctionNames.class.getFields()) {
            assertEquals(f.getName(), f.get(null));
        }
    }

    /**
     * Base class for dummy implementation of filter.
     */
    private static abstract class FilterBase implements ComparisonOperator<Object> {
        @Override public List<Expression<Object,?>> getExpressions() {return Collections.emptyList();}
        @Override public boolean test(Object resource) {return false;}
    }

    /**
     * Verifies the {@value FunctionNames#PROPERTY_IS_NULL} name.
     */
    @Test
    public void verifyPropertyIsNull() {
        final class Instanciable extends FilterBase implements NullOperator<Object> {}
        assertSame(new Instanciable().getOperatorType(), ComparisonOperatorName.valueOf(FunctionNames.PROPERTY_IS_NULL));
    }

    /**
     * Verifies the {@value FunctionNames#PROPERTY_IS_NIL} name.
     */
    @Test
    public void verifyPropertyIsNil() {
        final class Instanciable extends FilterBase implements NilOperator<Object> {}
        assertSame(new Instanciable().getOperatorType(), ComparisonOperatorName.valueOf(FunctionNames.PROPERTY_IS_NIL));
    }

    /**
     * Verifies the {@value FunctionNames#PROPERTY_IS_LIKE} name.
     */
    @Test
    public void verifyPropertyIsLike() {
        final class Instanciable extends FilterBase implements LikeOperator<Object> {}
        assertSame(new Instanciable().getOperatorType(), ComparisonOperatorName.valueOf(FunctionNames.PROPERTY_IS_LIKE));
    }

    /**
     * Verifies the {@value FunctionNames#PROPERTY_IS_BETWEEN} name.
     */
    @Test
    public void verifyPropertyIsBetween() {
        final class Instanciable extends FilterBase implements BetweenComparisonOperator<Object> {}
        assertSame(new Instanciable().getOperatorType(), ComparisonOperatorName.valueOf(FunctionNames.PROPERTY_IS_BETWEEN));
    }

    /**
     * Verifies the {@value FunctionNames#Literal} name.
     */
    @Test
    public void verifyLiteral() {
        final Literal<Object,Object> expression = new Literal<Object,Object>() {
            @Override public Object getValue() {return null;}
            @Override public <N> Expression<Object, N> toValueType(Class<N> target) {
                throw new UnsupportedOperationException();
            }
        };
        assertEquals(expression.getFunctionName().tip().toString(), FunctionNames.Literal);
    }

    /**
     * Verifies the {@value FunctionNames#ValueReference} name.
     */
    @Test
    public void verifyValueReference() {
        // TODO: use diamond operator with JDK9.
        final ValueReference<Object,Object> expression = new ValueReference<Object,Object>() {
            @Override public String getXPath()      {return null;}
            @Override public Object apply(Object o) {return null;}
            @Override public <N> Expression<Object,N> toValueType(Class<N> target) {
                throw new UnsupportedOperationException();
            }
        };
        assertEquals(expression.getFunctionName().tip().toString(), FunctionNames.ValueReference);
    }

    /**
     * Verifies SQLMM names.
     */
    @Test
    public void verifySQLMM() {
        int count = 0;
        for (final Field f : FunctionNames.class.getFields()) {
            final String name = f.getName();
            if (name.startsWith("ST_")) {
                assertEquals(name, SQLMM.valueOf(name).name());
                count++;
            }
        }
        assertEquals("count", 8, count);
    }
}
