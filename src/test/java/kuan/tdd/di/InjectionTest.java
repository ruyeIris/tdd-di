package kuan.tdd.di;

import jakarta.inject.Inject;
import kuan.tdd.di.exception.IllegalComponentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author qinxuekuan
 * @date 2022/6/24
 */
@Nested
class InjectionTest {

    ContextConfig config;

    @BeforeEach
    public void setup() {
        config = new ContextConfig();
    }

    @Nested
    public class ConstructorInjection {
        @Test
        public void should_bind_type_to_a_class_with_default_constructor() {

            Component instance = getComponent(Component.class, ComponentWithDefaultConstructor.class);

            assertNotNull(instance);
            assertTrue(instance instanceof ComponentWithDefaultConstructor);
        }

        @Test
        public void should_bind_type_to_a_class_with_inject_constructor() {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);

            Component instance = getComponent(Component.class, ComponentWithInjectConstructor.class);

            assertNotNull(instance);
            assertSame(dependency, ((ComponentWithInjectConstructor) instance).getDependency());
        }

        @Test
        public void should_bind_type_to_a_class_with_transitive_dependencies() {
            config.bind(Component.class, ComponentWithInjectConstructor.class);
            config.bind(Dependency.class, DependencyWithInjectConstructor.class);
            config.bind(String.class, "indirect dependency");

            Component instance = config.getContext().get(Component.class).get();
            assertNotNull(instance);

            Dependency dependency = ((ComponentWithInjectConstructor) instance).getDependency();
            assertNotNull(dependency);

            assertEquals("indirect dependency", ((DependencyWithInjectConstructor) dependency).getDependency());
        }

        @Test
        public void should_throw_exception_if_multi_inject_constructors_provided() {
            assertThrows(IllegalComponentException.class,
                    () -> new ConstructorInjectionProvider<>(ComponentWithMultiInjectConstructors.class));
        }

        @Test
        public void should_throw_exception_if_no_inject_nor_default_constructor_provided() {
            assertThrows(IllegalComponentException.class,
                    () -> new ConstructorInjectionProvider<>(ComponentWithNoInjectConstructorNorDefaultConstructor.class));
        }

        @Test
        public void should_include_dependency_from_inject_constructor() {
            ConstructorInjectionProvider<ComponentWithInjectConstructor> provider = new ConstructorInjectionProvider<>(ComponentWithInjectConstructor.class);
            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }


    }

    private <T, R extends T> T getComponent(Class<T> type, Class<R> implementation) {
        config.bind(type, implementation);
        return config.getContext().get(type).get();
    }

    @Nested
    public class FieldInjection {

        static class ComponentWithFieldInjection {
            @Inject
            private Dependency dependency;

            public Dependency getDependency() {
                return dependency;
            }
        }

        static class SubclassWithFieldInjection extends FieldInjection.ComponentWithFieldInjection {

        }

        // happy path。
        @Test
        public void should_inject_dependency_via_field() {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);


            FieldInjection.ComponentWithFieldInjection component = getComponent(FieldInjection.ComponentWithFieldInjection.class, FieldInjection.ComponentWithFieldInjection.class);

            assertSame(dependency, component.getDependency());
        }

        @Test
        public void should_inject_dependency_via_superclass_inject_field() {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);


            FieldInjection.SubclassWithFieldInjection component = getComponent(FieldInjection.SubclassWithFieldInjection.class, FieldInjection.SubclassWithFieldInjection.class);
            assertSame(dependency, component.getDependency());
        }

        // provider dependency information for field injection
        // 只要提供了足够的信息， ConfigContext 就会完成相应的对依赖异常情况的处理。
        // 依赖找不到的情况、循环依赖的 sad path 测试。
        @Test
        public void should_include_field_dependency_in_dependencies() {
            ConstructorInjectionProvider<FieldInjection.ComponentWithFieldInjection> provider =
                    new ConstructorInjectionProvider<>(FieldInjection.ComponentWithFieldInjection.class);

            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }


        static class FinalInjectField {
            @Inject
            final Dependency dependency = null;
        }

        @Test
        public void should_throw_exception_if_inject_field_is_final() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorInjectionProvider<>(FieldInjection.FinalInjectField.class));
        }

    }

    @Nested
    public class MethodInjection {

        static class InjectMethodWithNoDependency {
            boolean called = false;

            @Inject
            void install() {
                this.called = true;
            }
        }

        @Test
        public void should_call_inject_method_even_if_no_dependency_declared() {
            MethodInjection.InjectMethodWithNoDependency component = getComponent(MethodInjection.InjectMethodWithNoDependency.class, MethodInjection.InjectMethodWithNoDependency.class);
            assertTrue(component.called);
        }

        static class InjectMethodWithDependency {
            Dependency dependency;

            @Inject
            void install(Dependency dependency) {
                this.dependency = dependency;
            }
        }

        @Test
        public void should_inject_dependency_via_inject_method() {
            Dependency dependency = new Dependency() {
            };
            config.bind(Dependency.class, dependency);

            MethodInjection.InjectMethodWithDependency component = getComponent(MethodInjection.InjectMethodWithDependency.class, MethodInjection.InjectMethodWithDependency.class);

            assertSame(dependency, component.dependency);
        }

        @Test
        public void should_include_dependencies_from_inject_method() {
            ConstructorInjectionProvider<MethodInjection.InjectMethodWithDependency> provider =
                    new ConstructorInjectionProvider<>(MethodInjection.InjectMethodWithDependency.class);

            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }

        static class SuperClassWithInjectMethod {
            int superCalled = 0;

            @Inject
            void install() {
                this.superCalled++;
            }
        }

        static class SubclassWithInjectMethod extends MethodInjection.SuperClassWithInjectMethod {
            int subCalled = 0;

            @Inject
            void installAnother() {
                this.subCalled = super.superCalled + 1;
            }
        }

        @Test
        public void should_inject_dependencies_via_inject_method_from_superclass() {
            MethodInjection.SubclassWithInjectMethod component = getComponent(MethodInjection.SubclassWithInjectMethod.class, MethodInjection.SubclassWithInjectMethod.class);
            assertEquals(1, component.superCalled);
            assertEquals(2, component.subCalled);
        }

        static class SubclassOverrideSuperClassWithInject extends MethodInjection.SuperClassWithInjectMethod {
            @Inject
            @Override
            void install() {
                super.install();
            }
        }

        @Test
        public void should_only_call_once_if_subclass_override_inject_method_with_inject() {
            MethodInjection.SubclassOverrideSuperClassWithInject component = getComponent(MethodInjection.SubclassOverrideSuperClassWithInject.class, MethodInjection.SubclassOverrideSuperClassWithInject.class);

            assertEquals(1, component.superCalled);
        }

        static class SubclassOverrideSuperClassWithNoInject extends MethodInjection.SuperClassWithInjectMethod {
            @Override
            void install() {
                super.install();
            }
        }

        @Test
        public void should_not_call_inject_method_if_override_with_no_inject() {
            MethodInjection.SubclassOverrideSuperClassWithNoInject component = getComponent(MethodInjection.SubclassOverrideSuperClassWithNoInject.class, MethodInjection.SubclassOverrideSuperClassWithNoInject.class);

            assertEquals(0, component.superCalled);
        }

        static class InjectMethodWithTypeParameter {
            @Inject
            <T> void install() {

            }
        }

        @Test
        public void should_throw_exception_if_inject_method_has_type_parameter() {
            assertThrows(IllegalComponentException.class, () -> new ConstructorInjectionProvider<>(MethodInjection.InjectMethodWithTypeParameter.class));
        }
    }
}
