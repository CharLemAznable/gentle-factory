package com.github.charlemaznable.gentle.spring.factory.processor;

import com.google.auto.common.MoreElements;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.Set;

import static com.google.common.base.Throwables.getStackTraceAsString;

public abstract class AbstractCommonProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            processImpl(annotations, roundEnv);
        } catch (RuntimeException e) {
            // We don't allow exceptions of any kind to propagate to the compiler
            fatalError(getStackTraceAsString(e));
        }
        return false;
    }

    private void processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateConfigFiles();
        } else {
            processAnnotations(annotations, roundEnv);
        }
    }

    protected abstract void processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv);

    protected abstract void generateConfigFiles();

    protected static boolean rawTypesSuppressed(Element element) {
        for (; element != null; element = element.getEnclosingElement()) {
            SuppressWarnings suppress = element.getAnnotation(SuppressWarnings.class);
            if (suppress != null && Arrays.asList(suppress.value()).contains("rawtypes")) {
                return true;
            }
        }
        return false;
    }

    protected String getBinaryName(TypeElement element) {
        return getBinaryNameImpl(element, element.getSimpleName().toString());
    }

    private String getBinaryNameImpl(TypeElement element, String className) {
        Element enclosingElement = element.getEnclosingElement();

        if (enclosingElement instanceof PackageElement) {
            PackageElement pkg = MoreElements.asPackage(enclosingElement);
            if (pkg.isUnnamed()) {
                return className;
            }
            return pkg.getQualifiedName() + "." + className;
        }

        TypeElement typeElement = MoreElements.asType(enclosingElement);
        return getBinaryNameImpl(typeElement, typeElement.getSimpleName() + "$" + className);
    }

    protected void log(String msg) {
        if (processingEnv.getOptions().containsKey("debug")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
        }
    }

    protected void warning(String msg, Element element, AnnotationMirror annotation) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, element, annotation);
    }

    protected void error(String msg, Element element, AnnotationMirror annotation) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element, annotation);
    }

    protected void fatalError(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "FATAL ERROR: " + msg);
    }
}
