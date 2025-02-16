package kuan.tdd.di;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import kuan.tdd.di.exception.IllegalComponentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
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

    private final Provider<Dependency> dependencyProvider = mock(Provider.class);
    private final Dependency dependency = mock(Dependency.class);
    private final Context context = mock(Context.class);

    private ParameterizedType dependencyProviderType;

    @BeforeEach
    public void setup() throws NoSuchFieldException {
        dependencyProviderType =
                (ParameterizedType) InjectionTest.class.getDeclaredField("dependencyProvider").getGenericType();
        when(context.get(eq(ComponentRef.of(Dependency.class)))).thenReturn(Optional.of(dependency));
        when(context.get(eq(ComponentRef.of(dependencyProviderType)))).thenReturn(Optional.of(dependencyProvider));
    }

    @Nested
    public class ConstructorInjection {

        @Nested
        class Injection {

            @Test
            public void should_call_default_constructor_if_on_inject_constructor() {
                ComponentWithDefaultConstructor instance =
                        new InjectionProvider<>(ComponentWithDefaultConstructor.class).get(context);

                assertNotNull(instance);
            }

            @Test
            public void should_inject_dependency_via_inject_constructor() {
                ComponentWithInjectConstructor instance =
                        new InjectionProvider<>(ComponentWithInjectConstructor.class).get(context);

                assertNotNull(instance);
                assertSame(dependency, instance.getDependency());
            }

            // should_bind_type_to_a_class_with_transitive_dependencies 这个测试，
            // 实际和上面这个测试 should_bind_type_to_a_class_with_inject_constructor(should_inject_dependency_via_inject_constructor) 是一样的，
            // 也就是说，可以删除掉这个测试了。 是因为架构决策及测试粒度上的变化导致的。
            @Test
            @Disabled
            public void should_bind_type_to_a_class_with_transitive_dependencies() {
                when(context.get(eq(ComponentRef.of(Dependency.class)))).thenReturn(
                        Optional.of(new DependencyWithInjectConstructor("indirect dependency"))
                );
                ComponentWithInjectConstructor instance =
                        new InjectionProvider<>(ComponentWithInjectConstructor.class).get(context);

                assertNotNull(instance);

                Dependency dependency = instance.getDependency();
                assertNotNull(dependency);
            }

            @Test
            public void should_include_dependency_from_inject_constructor() {
                InjectionProvider<ComponentWithInjectConstructor> provider =
                        new InjectionProvider<>(ComponentWithInjectConstructor.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

            static class ProviderInjectConstructor{
                private Provider<Dependency> dependency;

                @Inject
                public ProviderInjectConstructor(Provider<Dependency> dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_provider_via_inject_constructor() {
                ProviderInjectConstructor instance = new InjectionProvider<>(ProviderInjectConstructor.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }

            @Test
            public void should_include_provider_type_from_inject_constructor() {
                InjectionProvider<ProviderInjectConstructor> provider = new InjectionProvider<>(ProviderInjectConstructor.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

        }


        @Nested
        class IllegalInjectConstructors {

            @Test
            public void should_throw_exception_if_multi_inject_constructors_provided() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(ComponentWithMultiInjectConstructors.class));
            }

            @Test
            public void should_throw_exception_if_no_inject_nor_default_constructor_provided() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(ComponentWithNoInjectConstructorNorDefaultConstructor.class));
            }


            abstract class AbstractComponent {
                @Inject
                public AbstractComponent() {
                }
            }

            @Test
            public void should_throw_exception_if_component_is_abstract() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(AbstractComponent.class));
            }

            @Test
            public void should_throw_exception_if_component_is_interface() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(TestComponent.class));
            }

        }


        @Nested
        class WithQualifier {

            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))))
                        .thenReturn(Optional.of(dependency));
            }

            static class InjectConstructor {
                Dependency dependency;

                @Inject
                public InjectConstructor(@Named("ChosenOne") Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_constructor() {
                InjectionProvider<InjectConstructor> provider = new InjectionProvider<>(InjectConstructor.class);
                InjectConstructor component = provider.get(context);
                assertSame(dependency, component.dependency);
            }


            @Test
            public void should_include_dependency_with_qualifier() {
                InjectionProvider<InjectConstructor> provider = new InjectionProvider<>(InjectConstructor.class);
                assertArrayEquals(
                        new ComponentRef<?>[]{ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))},
                        provider.getDependencies().toArray());
            }

            static class MultiQualifierInjectConstructor {
                @Inject
                public MultiQualifierInjectConstructor(@Named("ChosenOne") @Skywalker Dependency dependency) {

                }
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(MultiQualifierInjectConstructor.class));

            }
        }
    }

    @Nested
    public class FieldInjection {

        @Nested
        class Injection {
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
                        new InjectionProvider<>(ComponentWithFieldInjection.class).get(context);

                assertSame(dependency, component.getDependency());
            }

            @Test
            public void should_inject_dependency_via_superclass_inject_field() {
                SubclassWithFieldInjection component =
                        new InjectionProvider<>(SubclassWithFieldInjection.class).get(context);
                assertSame(dependency, component.getDependency());
            }

            // provider dependency information for field injection
            // 只要提供了足够的信息， ConfigContext 就会完成相应的对依赖异常情况的处理。
            // 依赖找不到的情况、循环依赖的 sad path 测试。
            @Test
            public void should_include_field_dependency_in_dependencies() {
                InjectionProvider<ComponentWithFieldInjection> provider =
                        new InjectionProvider<>(ComponentWithFieldInjection.class);

                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }


            static class ProviderInjectField{
                @Inject
                private Provider<Dependency> dependency;
            }

            @Test
            public void should_inject_provider_via_inject_method() {
                ProviderInjectField instance = new InjectionProvider<>(ProviderInjectField.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }

            @Test
            public void should_include_provider_type_from_inject_field() {
                InjectionProvider<ProviderInjectField> provider = new InjectionProvider<>(ProviderInjectField.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }
        }

        @Nested
        class IllegalInjectFields {
            static class FinalInjectField {
                @Inject
                final Dependency dependency = null;
            }

            @Test
            public void should_throw_exception_if_inject_field_is_final() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(FinalInjectField.class));
            }

        }

        @Nested
        class WithQualifier {
            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))))
                        .thenReturn(Optional.of(dependency));
            }

            static class InjectField {
                @Inject
                @Named("ChosenOne")
                Dependency dependency;
            }


            @Test
            public void should_inject_dependency_with_qualifier_via_field() {
                InjectionProvider<InjectField> provider = new InjectionProvider<>(InjectField.class);
                InjectField component = provider.get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependency_with_qualifier() {
                InjectionProvider<InjectField> provider = new InjectionProvider<>(InjectField.class);
                assertArrayEquals(
                        new ComponentRef<?>[]{ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))},
                        provider.getDependencies().toArray());
            }

            static class MultiQualifierInjectField {
                @Inject
                @Named("ChosenOne")
                @Skywalker
                Dependency dependency;
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(MultiQualifierInjectField.class));
            }
        }
    }

    @Nested
    public class MethodInjection {

        @Nested
        class Injection {
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
                        new InjectionProvider<>(InjectMethodWithNoDependency.class).get(context);
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
                        new InjectionProvider<>(InjectMethodWithDependency.class).get(context);

                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependencies_from_inject_method() {
                InjectionProvider<InjectMethodWithDependency> provider =
                        new InjectionProvider<>(InjectMethodWithDependency.class);

                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)}, provider.getDependencies().toArray(ComponentRef[]::new));
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
                        new InjectionProvider<>(SubclassWithInjectMethod.class).get(context);
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
                        new InjectionProvider<>(SubclassOverrideSuperClassWithInject.class).get(context);

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
                        new InjectionProvider<>(SubclassOverrideSuperClassWithNoInject.class).get(context);

                assertEquals(0, component.superCalled);
            }


            static class ProviderInjectMethod{
                private Provider<Dependency> dependency;

                @Inject
                void install(Provider<Dependency> dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_provider_via_inject_method() {
                ProviderInjectMethod instance = new InjectionProvider<>(ProviderInjectMethod.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }

            @Test
            public void should_include_provider_type_from_inject_method() {
                InjectionProvider<ProviderInjectMethod> provider = new InjectionProvider<>(ProviderInjectMethod.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

        }

        @Nested
        class IllegalInjectMethods {
            static class InjectMethodWithTypeParameter {
                @Inject
                <T> void install() {

                }
            }

            @Test
            public void should_throw_exception_if_inject_method_has_type_parameter() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(InjectMethodWithTypeParameter.class));
            }

        }

        @Nested
        class WithQualifier {

            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))))
                        .thenReturn(Optional.of(dependency));
            }


            static class InjectMethod {
                Dependency dependency;

                @Inject
                void install(@Named("ChosenOne") Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_method() {
                InjectionProvider<InjectMethod> provider = new InjectionProvider<>(InjectMethod.class);
                InjectMethod component = provider.get(context);
                assertSame(dependency, component.dependency);
            }


            @Test
            public void should_include_dependency_with_qualifier() {
                InjectionProvider<InjectMethod> provider = new InjectionProvider<>(InjectMethod.class);
                assertArrayEquals(
                        new ComponentRef<?>[]{ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))},
                        provider.getDependencies().toArray());
            }

            static class MultiQualifierInjectMethod {
                @Inject
                void install(@Named("ChosenOne") @Skywalker Dependency dependency) {
                }
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectionProvider<>(MultiQualifierInjectMethod.class));
            }
        }
    }
}
