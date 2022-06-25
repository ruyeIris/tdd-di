package kuan.tdd.di;

import jakarta.inject.Inject;
import kuan.tdd.di.exception.IllegalComponentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author qinxuekuan
 * @date 2022/6/24
 */
@Nested
class InjectionTest {

    private final Dependency dependency = mock(Dependency.class);
    private final Context context = mock(Context.class);


    @BeforeEach
    public void setup() {
        Mockito.when(context.get(eq(Dependency.class))).thenReturn(Optional.of(dependency));
    }

    @Nested
    public class ConstructorInjection {
        @Test
        public void should_bind_type_to_a_class_with_default_constructor() {
            ComponentWithDefaultConstructor instance =
                    new ConstructorInjectionProvider<>(ComponentWithDefaultConstructor.class).get(context);

            assertNotNull(instance);
        }

        @Test
        public void should_bind_type_to_a_class_with_inject_constructor() {
            ComponentWithInjectConstructor instance =
                    new ConstructorInjectionProvider<>(ComponentWithInjectConstructor.class).get(context);

            assertNotNull(instance);
            assertSame(dependency, instance.getDependency());
        }

        @Test
        public void should_bind_type_to_a_class_with_transitive_dependencies() {
            when(context.get(eq(Dependency.class))).thenReturn(
                    Optional.of(new DependencyWithInjectConstructor("indirect dependency"))
            );
            ComponentWithInjectConstructor instance =
                    new ConstructorInjectionProvider<>(ComponentWithInjectConstructor.class).get(context);

            assertNotNull(instance);

            Dependency dependency = instance.getDependency();
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
            ConstructorInjectionProvider<ComponentWithInjectConstructor> provider =
                    new ConstructorInjectionProvider<>(ComponentWithInjectConstructor.class);
            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }


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

        static class SubclassWithFieldInjection extends ComponentWithFieldInjection {

        }

        // happy path。
        @Test
        public void should_inject_dependency_via_field() {
            ComponentWithFieldInjection component =
                    new ConstructorInjectionProvider<>(ComponentWithFieldInjection.class).get(context);

            assertSame(dependency, component.getDependency());
        }

        @Test
        public void should_inject_dependency_via_superclass_inject_field() {
            SubclassWithFieldInjection component =
                    new ConstructorInjectionProvider<>(SubclassWithFieldInjection.class).get(context);
            assertSame(dependency, component.getDependency());
        }

        // provider dependency information for field injection
        // 只要提供了足够的信息， ConfigContext 就会完成相应的对依赖异常情况的处理。
        // 依赖找不到的情况、循环依赖的 sad path 测试。
        @Test
        public void should_include_field_dependency_in_dependencies() {
            ConstructorInjectionProvider<ComponentWithFieldInjection> provider =
                    new ConstructorInjectionProvider<>(ComponentWithFieldInjection.class);

            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }


        static class FinalInjectField {
            @Inject
            final Dependency dependency = null;
        }

        @Test
        public void should_throw_exception_if_inject_field_is_final() {
            assertThrows(IllegalComponentException.class,
                    () -> new ConstructorInjectionProvider<>(FinalInjectField.class));
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
            InjectMethodWithNoDependency component =
                    new ConstructorInjectionProvider<>(InjectMethodWithNoDependency.class).get(context);
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


            InjectMethodWithDependency component =
                    new ConstructorInjectionProvider<>(InjectMethodWithDependency.class).get(context);

            assertSame(dependency, component.dependency);
        }

        @Test
        public void should_include_dependencies_from_inject_method() {
            ConstructorInjectionProvider<InjectMethodWithDependency> provider =
                    new ConstructorInjectionProvider<>(InjectMethodWithDependency.class);

            assertArrayEquals(new Class<?>[]{Dependency.class}, provider.getDependencies().toArray(Class<?>[]::new));
        }

        static class SuperClassWithInjectMethod {
            int superCalled = 0;

            @Inject
            void install() {
                this.superCalled++;
            }
        }

        static class SubclassWithInjectMethod extends SuperClassWithInjectMethod {
            int subCalled = 0;

            @Inject
            void installAnother() {
                this.subCalled = super.superCalled + 1;
            }
        }

        @Test
        public void should_inject_dependencies_via_inject_method_from_superclass() {
            SubclassWithInjectMethod component =
                    new ConstructorInjectionProvider<>(SubclassWithInjectMethod.class).get(context);
            assertEquals(1, component.superCalled);
            assertEquals(2, component.subCalled);
        }

        static class SubclassOverrideSuperClassWithInject extends SuperClassWithInjectMethod {
            @Inject
            @Override
            void install() {
                super.install();
            }
        }

        @Test
        public void should_only_call_once_if_subclass_override_inject_method_with_inject() {
            SubclassOverrideSuperClassWithInject component =
                    new ConstructorInjectionProvider<>(SubclassOverrideSuperClassWithInject.class).get(context);

            assertEquals(1, component.superCalled);
        }

        static class SubclassOverrideSuperClassWithNoInject extends SuperClassWithInjectMethod {
            @Override
            void install() {
                super.install();
            }
        }

        @Test
        public void should_not_call_inject_method_if_override_with_no_inject() {
            SubclassOverrideSuperClassWithNoInject component =
                    new ConstructorInjectionProvider<>(SubclassOverrideSuperClassWithNoInject.class).get(context);

            assertEquals(0, component.superCalled);
        }

        static class InjectMethodWithTypeParameter {
            @Inject
            <T> void install() {

            }
        }

        @Test
        public void should_throw_exception_if_inject_method_has_type_parameter() {
            assertThrows(IllegalComponentException.class,
                    () -> new ConstructorInjectionProvider<>(InjectMethodWithTypeParameter.class));
        }
    }
}
