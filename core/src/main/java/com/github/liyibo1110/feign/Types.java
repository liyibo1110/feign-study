package com.github.liyibo1110.feign;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * 用于处理Type的工具方法。
 *
 * 先复习一遍Type的几种具体化：
 * 1、Class：String.class / Integer.class / User[].class
 * 2、ParameterizedType：List<String> / Map<String, Integer> / Response<User>
 * 3、TypeVariable：即类型变量，说的是泛型里面的T / E / K / V
 * 4、WildcardType：List<?> / List<? extends Number> / List<? super Integer>
 * 5、GenericArrayType：组件类型本身也带泛型的数组，例如T[] / List<String>[]，注意它和普通数组不一样
 *
 * @author liyibo
 * @date 2026-04-23 10:43
 */
public final class Types {
    private Types() {}

    private static final Type[] EMPTY_TYPE_ARRAY = new Type[0];

    /**
     * 根据给定的Type，返回真正对应的Class对象。
     *
     * 作用：无论传进来哪种Type，尽量提取出它背后的原始class。
     * getRawType(String.class)                -> String.class
     * getRawType(List<String>)                -> List.class
     * getRawType(? extends Number)            -> Number.class
     * getRawType(T)                           -> Object.class
     * getRawType(List<String>[])              -> List[].class
     */
    public static Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) { // 直接返回自己
            // Type is a normal class.
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) { // 取getRawType()，例如List<String>，会返回List.class
            ParameterizedType parameterizedType = (ParameterizedType) type;
            // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
            // suspects some pathological case related to nested classes exists.
            Type rawType = parameterizedType.getRawType();
            if (!(rawType instanceof Class))
                throw new IllegalArgumentException();

            return (Class<?>) rawType;
        } else if (type instanceof GenericArrayType) {  // 取组件类型的raw type，再构造数组Class
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            return Array.newInstance(getRawType(componentType), 0).getClass();
        } else if (type instanceof TypeVariable) {  // 直接返回Object.class，因为不是个具体的类型
            // We could use the variable's bounds, but that won't work if there are multiple. Having a raw
            // type that's more general than necessary is okay.
            return Object.class;
        } else if (type instanceof WildcardType) {  // 返回上界的raw type
            return getRawType(((WildcardType) type).getUpperBounds()[0]);
        } else {
            String className = type == null ? "null" : type.getClass().getName();
            throw new IllegalArgumentException(
                    "Expected a Class, ParameterizedType, or "
                            + "GenericArrayType, but <"
                            + type
                            + "> is of type "
                            + className);
        }
    }

    /**
     * 判断2个Type是否相等。
     */
    static boolean equals(Type a, Type b) {
        if (a == b) {
            return true; // Also handles (a == null && b == null).

        } else if (a instanceof Class) {
            return a.equals(b); // Class already specifies equals().

        /**
         * 1、ownerType相等。
         * 2、rawType相等。
         * 3、actualTypeArguments数组相等。
         */
        } else if (a instanceof ParameterizedType) {
            if (!(b instanceof ParameterizedType))
                return false;

            ParameterizedType pa = (ParameterizedType) a;
            ParameterizedType pb = (ParameterizedType) b;
            return equal(pa.getOwnerType(), pb.getOwnerType())
                    && pa.getRawType().equals(pb.getRawType())
                    && Arrays.equals(pa.getActualTypeArguments(), pb.getActualTypeArguments());

        } else if (a instanceof GenericArrayType) {
            if (!(b instanceof GenericArrayType))
                return false;

            GenericArrayType ga = (GenericArrayType) a;
            GenericArrayType gb = (GenericArrayType) b;
            return equals(ga.getGenericComponentType(), gb.getGenericComponentType());
        } else if (a instanceof WildcardType) {
            if (!(b instanceof WildcardType))
                return false;

            WildcardType wa = (WildcardType) a;
            WildcardType wb = (WildcardType) b;
            return Arrays.equals(wa.getUpperBounds(), wb.getUpperBounds())
                    && Arrays.equals(wa.getLowerBounds(), wb.getLowerBounds());
        } else if (a instanceof TypeVariable) {
            if (!(b instanceof TypeVariable))
                return false;
            TypeVariable<?> va = (TypeVariable<?>) a;
            TypeVariable<?> vb = (TypeVariable<?>) b;
            return va.getGenericDeclaration() == vb.getGenericDeclaration() && va.getName().equals(vb.getName());
        } else {
            return false; // This isn't a type we support!
        }
    }

    /**
     * 返回supertype的泛型超类型。
     * 例如，对于类IntegerSet，当supertype为 Set.class时，结果为 Set<Integer>。
     * 当supertype为Collection.class时，结果为Collection<Integer>。
     *
     * 作用：沿着继承树/接口树向上找，找到某个目标父类在当前上下文中的泛型形式，就是向上找泛型的祖先。
     */
    static Type getGenericSupertype(Type context, Class<?> rawType, Class<?> toResolve) {
        if (toResolve == rawType)
            return context;

        // We skip searching through interfaces if unknown is an interface.
        if (toResolve.isInterface()) {
            Class<?>[] interfaces = rawType.getInterfaces();
            for (int i = 0, length = interfaces.length; i < length; i++) {
                if (interfaces[i] == toResolve)
                    return rawType.getGenericInterfaces()[i];
                else if (toResolve.isAssignableFrom(interfaces[i]))
                    return getGenericSupertype(rawType.getGenericInterfaces()[i], interfaces[i], toResolve);

            }
        }

        // Check our supertypes.
        if (!rawType.isInterface()) {
            while (rawType != Object.class) {
                Class<?> rawSupertype = rawType.getSuperclass();
                if (rawSupertype == toResolve)
                    return rawType.getGenericSuperclass();
                else if (toResolve.isAssignableFrom(rawSupertype))
                    return getGenericSupertype(rawType.getGenericSuperclass(), rawSupertype, toResolve);

                rawType = rawSupertype;
            }
        }

        // We can't resolve this further.
        return toResolve;
    }

    private static int indexOf(Object[] array, Object toFind) {
        for (int i = 0; i < array.length; i++) {
            if (toFind.equals(array[i]))
                return i;
        }
        throw new NoSuchElementException();
    }

    private static boolean equal(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    private static int hashCodeOrZero(Object o) {
        return o != null ? o.hashCode() : 0;
    }

    static String typeToString(Type type) {
        return type instanceof Class ? ((Class<?>) type).getName() : type.toString();
    }

    /**
     * 返回超类的通用形式。
     * 例如，如果该超类是ArrayList<String>，那么当输入为Iterable.class时，此方法将返回Iterable<String>。
     */
    static Type getSupertype(Type context, Class<?> contextRawType, Class<?> supertype) {
        if (!supertype.isAssignableFrom(contextRawType))
            throw new IllegalArgumentException();

        return resolve(context, contextRawType, getGenericSupertype(context, contextRawType, supertype));
    }

    /**
     * 工具类的核心方法：在某个上下文中，把一个Type里的类型变量递归替换成具体的类型。
     */
    public static Type resolve(Type context, Class<?> contextRawType, Type toResolve) {
        // This implementation is made a little more complicated in an attempt to avoid object-creation.
        while (true) {
            if (toResolve instanceof TypeVariable) {
                TypeVariable<?> typeVariable = (TypeVariable<?>) toResolve;
                toResolve = resolveTypeVariable(context, contextRawType, typeVariable);
                if (toResolve == typeVariable) {
                    return toResolve;
                }

            } else if (toResolve instanceof Class && ((Class<?>) toResolve).isArray()) {
                Class<?> original = (Class<?>) toResolve;
                Type componentType = original.getComponentType();
                Type newComponentType = resolve(context, contextRawType, componentType);
                return componentType == newComponentType
                        ? original
                        : new GenericArrayTypeImpl(newComponentType);

            } else if (toResolve instanceof GenericArrayType) {
                GenericArrayType original = (GenericArrayType) toResolve;
                Type componentType = original.getGenericComponentType();
                Type newComponentType = resolve(context, contextRawType, componentType);
                return componentType == newComponentType
                        ? original
                        : new GenericArrayTypeImpl(newComponentType);

            } else if (toResolve instanceof ParameterizedType) {
                ParameterizedType original = (ParameterizedType) toResolve;
                Type ownerType = original.getOwnerType();
                Type newOwnerType = resolve(context, contextRawType, ownerType);
                boolean changed = newOwnerType != ownerType;

                Type[] args = original.getActualTypeArguments();
                for (int t = 0, length = args.length; t < length; t++) {
                    Type resolvedTypeArgument = resolve(context, contextRawType, args[t]);
                    if (resolvedTypeArgument != args[t]) {
                        if (!changed) {
                            args = args.clone();
                            changed = true;
                        }
                        args[t] = resolvedTypeArgument;
                    }
                }

                return changed
                        ? new ParameterizedTypeImpl(newOwnerType, original.getRawType(), args)
                        : original;

            } else if (toResolve instanceof WildcardType) {
                WildcardType original = (WildcardType) toResolve;
                Type[] originalLowerBound = original.getLowerBounds();
                Type[] originalUpperBound = original.getUpperBounds();

                if (originalLowerBound.length == 1) {
                    Type lowerBound = resolve(context, contextRawType, originalLowerBound[0]);
                    if (lowerBound != originalLowerBound[0])
                        return new WildcardTypeImpl(new Type[] {Object.class}, new Type[] {lowerBound});
                } else if (originalUpperBound.length == 1) {
                    Type upperBound = resolve(context, contextRawType, originalUpperBound[0]);
                    if (upperBound != originalUpperBound[0])
                        return new WildcardTypeImpl(new Type[] {upperBound}, EMPTY_TYPE_ARRAY);
                }
                return original;

            } else {
                return toResolve;
            }
        }
    }

    private static Type resolveTypeVariable(Type context, Class<?> contextRawType, TypeVariable<?> unknown) {
        Class<?> declaredByRaw = declaringClassOf(unknown);

        // We can't reduce this further.
        if (declaredByRaw == null)
            return unknown;

        Type declaredBy = getGenericSupertype(context, contextRawType, declaredByRaw);
        if (declaredBy instanceof ParameterizedType) {
            int index = indexOf(declaredByRaw.getTypeParameters(), unknown);
            return ((ParameterizedType) declaredBy).getActualTypeArguments()[index];
        }

        return unknown;
    }

    /**
     * 返回typeVariable的声明类，如果该变量未由类声明，则返回null。
     */
    private static Class<?> declaringClassOf(TypeVariable<?> typeVariable) {
        GenericDeclaration genericDeclaration = typeVariable.getGenericDeclaration();
        return genericDeclaration instanceof Class ? (Class<?>) genericDeclaration : null;
    }

    private static void checkNotPrimitive(Type type) {
        if (type instanceof Class<?> && ((Class<?>) type).isPrimitive())
            throw new IllegalArgumentException();
    }

    public static Type resolveReturnType(Type baseType, Type overridingType) {
        if (baseType instanceof Class
                && overridingType instanceof Class
                && ((Class<?>) baseType).isAssignableFrom((Class<?>) overridingType)) {
            // NOTE: javac generates multiple same methods for multiple inherited generic interfaces
            return overridingType;
        }
        if (baseType instanceof Class && overridingType instanceof ParameterizedType) {
            // NOTE: javac will generate multiple methods with different return types
            // base interface declares generic method, override declares parameterized generic method
            return overridingType;
        }
        if (baseType instanceof Class && overridingType instanceof TypeVariable) {
            // NOTE: javac will generate multiple methods with different return types
            // base interface declares non generic method, override declares generic method
            return overridingType;
        }
        return baseType;
    }

    /**
     * 根据genericContext，将泛型超类的最后一个类型参数解析为其上界。
     * 实现代码复制自retrofit.RestMethodInfo。
     *
     * 作用：把某个父类型最后一个泛型参数解析出来
     */
    public static Type resolveLastTypeParameter(Type genericContext, Class<?> supertype) throws IllegalStateException {
        Type resolvedSuperType = Types.getSupertype(genericContext, Types.getRawType(genericContext), supertype);
        Util.checkState(
                resolvedSuperType instanceof ParameterizedType,
                "could not resolve %s into a parameterized type %s",
                genericContext,
                supertype);
        Type[] types = ParameterizedType.class.cast(resolvedSuperType).getActualTypeArguments();
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            if (type instanceof WildcardType)
                types[i] = ((WildcardType) type).getUpperBounds()[0];
        }
        return types[types.length - 1];
    }

    public static ParameterizedType parameterize(Class<?> rawClass, Type... typeArguments) {
        return new ParameterizedTypeImpl(rawClass.getEnclosingClass(), rawClass, typeArguments);
    }

    /**
     * 以下为ParameterizedType接口的原始注释：
     *
     * ParameterizedType表示一个泛型类型，例如Collection<String>。
     * 根据本包的规范，当反射方法首次需要某个泛型类型时，该类型才会被创建。
     *
     * 当创建泛型类型p时，会解析p所实例化的泛型类或接口声明，并递归地创建p的所有类型参数。
     * 有关类型变量创建过程的详细信息，请参阅TypeVariable。重复创建参数化类型不会产生任何影响。
     *
     * 实现此接口的类实例必须实现equals()方法，该方法将任何两个具有相同泛型类或接口声明且类型参数相等的实例视为相等。
     *
     * 作用：补全JDK反射类型对象可构造能力的缺口（后面的自定义类全是），JDK虽然给了接口，但是没给方便的公共实现类让开发者直接new，
     * 很多框架都会自己搞一套这样的实现类。
     */
    static final class ParameterizedTypeImpl implements ParameterizedType {
        /** 拥有者类型，主要用于内部类的场景，ownerType标识自身的上层Outer类 */
        private final Type ownerType;

        /** 原始类型，例如List.class */
        private final Type rawType;

        /** 泛型实参，例如[String.class] */
        private final Type[] typeArguments;

        ParameterizedTypeImpl(Type ownerType, Type rawType, Type... typeArguments) {
            // Require an owner type if the raw type needs it.
            if (rawType instanceof Class<?> && (ownerType == null) != (((Class<?>) rawType).getEnclosingClass() == null))
                throw new IllegalArgumentException();

            this.ownerType = ownerType;
            this.rawType = rawType;
            this.typeArguments = typeArguments.clone();

            for (Type typeArgument : this.typeArguments) {
                if (typeArgument == null)
                    throw new NullPointerException();

                checkNotPrimitive(typeArgument);
            }
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ParameterizedType && Types.equals(this, (ParameterizedType) other);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(typeArguments) ^ rawType.hashCode() ^ hashCodeOrZero(ownerType);
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(30 * (typeArguments.length + 1));
            result.append(typeToString(rawType));
            if (typeArguments.length == 0)
                return result.toString();

            result.append("<").append(typeToString(typeArguments[0]));
            for (int i = 1; i < typeArguments.length; i++)
                result.append(", ").append(typeToString(typeArguments[i]));

            return result.append(">").toString();
        }
    }

    /**
     * 以下为GenericArrayType接口的原始注释：
     *
     * GenericArrayType表示一种数组类型，其组成元素的类型可以是参数化类型，也可以是类型变量。
     */
    private static final class GenericArrayTypeImpl implements GenericArrayType {
        private final Type componentType;

        GenericArrayTypeImpl(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GenericArrayType && Types.equals(this, (GenericArrayType) o);
        }

        @Override
        public int hashCode() {
            return componentType.hashCode();
        }

        @Override
        public String toString() {
            return typeToString(componentType) + "[]";
        }
    }

    /**
     * 以下为WildcardType接口的原始注释：
     *
     * WildcardType表示一个通配符类型表达式，例如?、? extends Number或?。
     *
     * 以下为WildcardTypeImpl实现类的注释：
     * WildcardType接口支持多个上界和多个下界。我们仅支持Java 6语言的要求——最多一个界限。如果设置了下界，上界必须是Object.class。
     *
     * 在这里复习通配符泛型：
     * 1、? 本质上等同于 ? extends Object
     * 2、? extends Number这种，只会有upperBound（为Number），没有lowerBound。
     * 3、? super Integer这种，lowerBound就是Integer，upperBound必须是Object。
     */
    static final class WildcardTypeImpl implements WildcardType {
        private final Type upperBound;
        private final Type lowerBound;

        WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
            if (lowerBounds.length > 1)
                throw new IllegalArgumentException();

            if (upperBounds.length != 1)
                throw new IllegalArgumentException();

            if (lowerBounds.length == 1) {
                if (lowerBounds[0] == null)
                    throw new NullPointerException();

                checkNotPrimitive(lowerBounds[0]);
                if (upperBounds[0] != Object.class)
                    throw new IllegalArgumentException();

                this.lowerBound = lowerBounds[0];
                this.upperBound = Object.class;
            } else {
                if (upperBounds[0] == null)
                    throw new NullPointerException();

                checkNotPrimitive(upperBounds[0]);
                this.lowerBound = null;
                this.upperBound = upperBounds[0];
            }
        }

        @Override
        public Type[] getUpperBounds() {
            return new Type[] {upperBound};
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBound != null ? new Type[] {lowerBound} : EMPTY_TYPE_ARRAY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof WildcardType && Types.equals(this, (WildcardType) other);
        }

        @Override
        public int hashCode() {
            // This equals Arrays.hashCode(getLowerBounds()) ^ Arrays.hashCode(getUpperBounds()).
            return (lowerBound != null ? 31 + lowerBound.hashCode() : 1) ^ (31 + upperBound.hashCode());
        }

        @Override
        public String toString() {
            if (lowerBound != null)
                return "? super " + typeToString(lowerBound);
            if (upperBound == Object.class)
                return "?";
            return "? extends " + typeToString(upperBound);
        }
    }
}
