package kuan.tdd.di;

import jakarta.inject.Inject;
import kuan.tdd.di.exception.IllegalComponentException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author qinxuekuan
 * @date 2022/6/17
 */
class ConstructorInjectionProvider<T> implements ContextConfig.ComponentProvider<T> {
    private final Constructor<T> injectConstructor;

    public ConstructorInjectionProvider(Class<T> component) {
        injectConstructor = getInjectConstructor(component);
    }

    static private <Type> Constructor<Type> getInjectConstructor(Class<Type> implementation) {
        List<Constructor<?>> injectConstructors = Arrays.stream(implementation.getConstructors())
                .filter(c -> c.isAnnotationPresent(Inject.class)).toList();
        if (injectConstructors.size() > 1) {
            throw new IllegalComponentException();
        }

        return (Constructor<Type>) injectConstructors.stream().findFirst().orElseGet(() -> {
            try {
                return implementation.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalComponentException();
            }
        });
    }

    @Override
    public T get(Context context) {
        try {
            Object[] dependencies = Arrays.stream(injectConstructor.getParameters())
                    .map(p -> context.get(p.getType()).get())
                    .toArray(Object[]::new);
            return (T) injectConstructor.newInstance(dependencies);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Class<?>> getDependencies() {
        return Arrays.stream(injectConstructor.getParameters()).map(Parameter::getType).collect(Collectors.toList());
    }

}
