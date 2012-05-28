/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.common.beans.property;

import java.beans.PropertyEditor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * @author baranowb
 *
 */
public class PropertyEditorInstanceFinder {

    private static Logger log = Logger.getLogger(PropertyEditorInstanceFinder.class.getName());

    private static final String _ARRAY = "Array";
    private static final String _EDITOR = "Editor";
    private static final String NULL = "null";

    /** Primitive type name -> class map. */
    private static final Map<String, Class<?>> PRIMITIVE_NAME_TYPE_MAP;

    /** Setup the primitives map. */
    static {
        Map<String, Class<?>> tmp = new HashMap<String, Class<?>>();
        // Map<String,Class<?>> tmp= new HashMap<String,Class<?>>();
        tmp.put("boolean", Boolean.TYPE);
        tmp.put("byte", Byte.TYPE);
        tmp.put("char", Character.TYPE);
        tmp.put("short", Short.TYPE);
        tmp.put("int", Integer.TYPE);
        tmp.put("long", Long.TYPE);
        tmp.put("float", Float.TYPE);
        tmp.put("double", Double.TYPE);
        PRIMITIVE_NAME_TYPE_MAP = Collections.unmodifiableMap(tmp);
    }
    private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPERS;
    static {
        Map<Class<?>, Class<?>> tmp2 = new HashMap<Class<?>, Class<?>>();
        tmp2.put(boolean.class, Boolean.class);
        tmp2.put(byte.class, Byte.class);
        tmp2.put(char.class, Character.class);
        tmp2.put(double.class, Double.class);
        tmp2.put(float.class, Float.class);
        tmp2.put(int.class, Integer.class);
        tmp2.put(long.class, Long.class);
        tmp2.put(short.class, Short.class);
        // tmp2.put(void.class, Void.class)
        PRIMITIVES_TO_WRAPPERS = Collections.unmodifiableMap(tmp2);
    }

    private static final HashMap<String, PropertyEditor> _EDITORS = new HashMap<String, PropertyEditor>();

    static {
        // this requires edited classes to be in CL scope.
        final ServiceLoader<PropertyEditor> service = ServiceLoader.load(PropertyEditor.class);
        for (PropertyEditor propertyEditor : service) {
            final String propertyEditorClassName = propertyEditor.getClass().getName();
            String typeEditorName = propertyEditorClassName.substring(propertyEditorClassName.lastIndexOf('.') + 1,
                    propertyEditorClassName.length());
            typeEditorName = typeEditorName.substring(0, typeEditorName.lastIndexOf(_EDITOR));
            if (_EDITORS.containsKey(typeEditorName)) {
                log.warning("Editor for '" + typeEditorName + "' alredy defined '"
                        + _EDITORS.get(typeEditorName).getClass().getName() + "', ignoring '" + propertyEditorClassName + "' !");

            } else {
                _EDITORS.put(typeEditorName, propertyEditor);
            }
        }
    }

    public static PropertyEditor findEditor(Class<?> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("Target type must not be null.");
        }
        boolean isArray = targetType.isArray();

        if (isArray) {
            Class<?> elementType = targetType.getComponentType();
            if (elementType.isPrimitive()) {
                elementType = getWrapperTypeFor(elementType);
            }
            final String typeEditorName = stripPackage(elementType);
            final String arrayTypeEditorName = typeEditorName + _ARRAY;
            PropertyEditor propertyEditor = _EDITORS.get(arrayTypeEditorName);
            if (propertyEditor == null && _EDITORS.get(typeEditorName) != null) {
                propertyEditor = new GenericArrayPropertyEditor(targetType);
            }
            return propertyEditor;
        } else {
            if (targetType.isPrimitive()) {
                targetType = getWrapperTypeFor(targetType);
            }
            final String typeEditorName = stripPackage(targetType);
            return _EDITORS.get(typeEditorName);
        }
    }

    /**
     * Locate a value editor for a given target type.
     *
     * @param typeName The class name of the object to be edited.
     * @return An editor for the given type or null if none was found.
     * @throws ClassNotFoundException when the class could not be found
     */
    public static PropertyEditor findEditor(final String typeName) throws ClassNotFoundException {
        // see if it is a primitive type first
        Class<?> type = getPrimitiveTypeForName(typeName);
        if (type == null) {
            // nope try look up
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            type = loader.loadClass(typeName);
        }
        return findEditor(type);
    }

    /**
     * Whether a string is interpreted as the null value, including the empty string.
     *
     * @param value the value
     * @return true when the string has the value null
     */
    public static final boolean isNull(final String value) {
        return isNull(value, true, true);
    }

    /**
     * Whether a string is interpreted as the null value
     *
     * @param value the value
     * @param trim whether to trim the string
     * @param empty whether to include the empty string as null
     * @return true when the string has the value null
     */
    public static final boolean isNull(final String value, final boolean trim, final boolean empty) {
        // No value?
        if (value == null)
            return true;
        // Trim the text when requested
        String trimmed = trim ? value.trim() : value;
        // Is the empty string null?
        if (empty && trimmed.length() == 0)
            return true;
        // Just check it.
        return NULL.equalsIgnoreCase(trimmed);
    }

    private static String stripPackage(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1, fqn.length());
    }

    private static String stripPackage(Class<?> clazz) {
        final String fqn = clazz.getName();
        return fqn.substring(fqn.lastIndexOf('.') + 1, fqn.length());
    }

    public static Class<?> getPrimitiveTypeForName(final String name) {
        return (Class<?>) PRIMITIVE_NAME_TYPE_MAP.get(name);
    }

    public static Class<?> getWrapperTypeFor(final Class<?> primitive) {
        return (Class<?>) PRIMITIVES_TO_WRAPPERS.get(primitive);
    }

}
