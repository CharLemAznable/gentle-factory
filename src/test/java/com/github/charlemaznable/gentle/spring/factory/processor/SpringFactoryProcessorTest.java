package com.github.charlemaznable.gentle.spring.factory.processor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

import static com.github.charlemaznable.gentle.spring.factory.processor.SpringFactoryProcessor.MISSING_SERVICES_ERROR;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class SpringFactoryProcessorTest {

    @Test
    public void autoService() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor()).compile(
                        JavaFileObjects.forResource("test/SomeService.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider1.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider2.java"),
                        JavaFileObjects.forResource("test/Enclosing.java"),
                        JavaFileObjects.forResource("test/AnotherService.java"),
                        JavaFileObjects.forResource("test/AnotherServiceProvider.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Multimap<String, String> services = generatedServices(compilation);
        Collection<String> someServices = services.get("test.SomeService");
        assertEquals(3, someServices.size());
        assertTrue(someServices.contains("test.Enclosing$NestedSomeServiceProvider"));
        assertTrue(someServices.contains("test.SomeServiceProvider1"));
        assertTrue(someServices.contains("test.SomeServiceProvider2"));
        Collection<String> anotherServices = services.get("test.AnotherService");
        assertEquals(1, anotherServices.size());
        assertTrue(anotherServices.contains("test.AnotherServiceProvider"));
    }

    @Test
    public void multiService() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor()).compile(
                        JavaFileObjects.forResource("test/SomeService.java"),
                        JavaFileObjects.forResource("test/AnotherService.java"),
                        JavaFileObjects.forResource("test/MultiServiceProvider.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Multimap<String, String> services = generatedServices(compilation);
        Collection<String> someServices = services.get("test.SomeService");
        assertEquals(1, someServices.size());
        assertTrue(someServices.contains("test.MultiServiceProvider"));
        Collection<String> anotherServices = services.get("test.AnotherService");
        assertEquals(1, anotherServices.size());
        assertTrue(anotherServices.contains("test.MultiServiceProvider"));
    }

    @Test
    public void badMultiService() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .compile(JavaFileObjects.forResource("test/NoServices.java"));
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(MISSING_SERVICES_ERROR);
    }

    @Test
    public void generic() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor()).compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/GenericServiceProvider.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Multimap<String, String> services = generatedServices(compilation);
        Collection<String> genericServices = services.get("test.GenericService");
        assertEquals(1, genericServices.size());
        assertTrue(genericServices.contains("test.GenericServiceProvider"));
    }

    @Test
    public void genericWithVerifyOption() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .withOptions("-Averify=true", "-Adebug").compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/GenericServiceProvider.java"));
        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining(
                "Service provider test.GenericService is generic, so it can't be named exactly by"
                        + " @SpringFactory. If this is OK, add @SuppressWarnings(\"rawtypes\").");
    }

    @Test
    public void genericWithVerifyOptionAndSuppressWarings() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .withOptions("-Averify=true", "-Adebug").compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/GenericServiceProviderSuppressWarnings.java"));
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    public void nestedGenericWithVerifyOptionAndSuppressWarnings() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .withOptions("-Averify=true", "-Adebug").compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/EnclosingGeneric.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Multimap<String, String> services = generatedServices(compilation);
        Collection<String> genericServices = services.get("test.GenericService");
        assertEquals(1, genericServices.size());
        assertTrue(genericServices.contains("test.EnclosingGeneric$GenericServiceProvider"));
    }

    private Multimap<String, String> generatedServices(Compilation compilation) {
        Optional<JavaFileObject> javaFileObject = compilation
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/spring.factories");
        assertTrue(javaFileObject.isPresent());
        try {
            return FactoriesFile.readServiceFile(javaFileObject.get().openInputStream());
        } catch (IOException e) {
            return HashMultimap.create();
        }
    }
}
