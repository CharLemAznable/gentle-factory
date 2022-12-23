package com.github.charlemaznable.gentle.spring.factory.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.charlemaznable.gentle.spring.factory.processor.AutoConfigurationImportsFile.IMPORTS_FILE_PATH;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class AutoConfigurationImportProcessorTest {

    @Test
    public void autoConfig() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new AutoConfigurationImportProcessor()).compile(
                        JavaFileObjects.forResource("test/SomeService.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider1.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider2.java"));
        assertThat(compilation).succeededWithoutWarnings();

        List<String> services = generatedServices(compilation);
        assertTrue(services.contains("test.SomeServiceProvider1"));
        assertTrue(services.contains("test.SomeServiceProvider2"));
    }

    @Test
    public void addAutoConfig() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new AutoConfigurationImportProcessor()).compile(
                        JavaFileObjects.forResource("test/SomeService.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider1.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider2.java"),
                        JavaFileObjects.forResource("test/Enclosing.java"),
                        JavaFileObjects.forResource("test/AnotherService.java"),
                        JavaFileObjects.forResource("test/AnotherServiceProvider.java"));
        assertThat(compilation).succeededWithoutWarnings();

        List<String> services = generatedServices(compilation);
        assertTrue(services.contains("test.SomeServiceProvider1"));
        assertTrue(services.contains("test.SomeServiceProvider2"));
        assertTrue(services.contains("test.Enclosing$NestedSomeServiceProvider"));
        assertTrue(services.contains("test.AnotherServiceProvider"));
    }

    private List<String> generatedServices(Compilation compilation) {
        Optional<JavaFileObject> javaFileObject = compilation
                .generatedFile(StandardLocation.CLASS_OUTPUT, IMPORTS_FILE_PATH);
        assertTrue(javaFileObject.isPresent());
        try {
            return AutoConfigurationImportsFile.readServiceFile(javaFileObject.get().openInputStream());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
