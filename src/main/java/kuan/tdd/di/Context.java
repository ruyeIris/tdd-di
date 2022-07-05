package kuan.tdd.di;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * @author qinxuekuan
 * @date 2022/6/15
 */
public interface Context {
    /**
     * @param type   从容器中需要获取组件的类型
     * @param <Type> 类型使用 Type 这个泛型表示
     * @return 一个组件，可以 null。 用 Optional 装载
     */
    <Type> Optional<Type> get(Class<Type> type);

    Optional get(ParameterizedType type);

    default Optional getType(Type type) {
        if (type instanceof ParameterizedType) {
            return get((ParameterizedType) type);
        }
        return get((Class<?>) type);
    }
}
