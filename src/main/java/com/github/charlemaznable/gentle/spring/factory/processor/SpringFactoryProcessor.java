package com.github.charlemaznable.gentle.spring.factory.processor;

import com.github.charlemaznable.gentle.spring.factory.SpringFactory;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.charlemaznable.gentle.spring.factory.processor.SpringFactoriesFile.FACTORIES_FILE_PATH;
import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

@AutoService(Processor.class)
@SupportedOptions({"debug", "verify"})
public final class SpringFactoryProcessor extends AbstractCommonProcessor {

    @VisibleForTesting
    static final String MISSING_SERVICES_ERROR = "No service interfaces provided for element!";

    private final Multimap<String, String> providers = HashMultimap.create();

    @Override
    public ImmutableSet<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(SpringFactory.class.getName());
    }

    protected void processAnnotations(Set<? extends TypeElement> annotations,
                                    RoundEnvironment roundEnv) {

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(SpringFactory.class);

        log(annotations.toString());
        log(elements.toString());

        for (Element e : elements) {
            // (gak): check for error trees?
            TypeElement providerImplementer = MoreElements.asType(e);
            AnnotationMirror annotationMirror = getAnnotationMirror(e, SpringFactory.class).orNull();
            Set<DeclaredType> providerInterfaces = getValueFieldOfClasses(annotationMirror);
            if (providerInterfaces.isEmpty()) {
                error(MISSING_SERVICES_ERROR, e, annotationMirror);
                continue;
            }
            for (DeclaredType providerInterface : providerInterfaces) {
                TypeElement providerType = MoreTypes.asTypeElement(providerInterface);

                log("provider interface: " + providerType.getQualifiedName());
                log("provider implementer: " + providerImplementer.getQualifiedName());

                if (checkImplementer(providerImplementer, providerType, annotationMirror)) {
                    providers.put(getBinaryName(providerType), getBinaryName(providerImplementer));
                } else {
                    String message = "ServiceProviders must implement their service provider interface. "
                            + providerImplementer.getQualifiedName() + " does not implement "
                            + providerType.getQualifiedName();
                    error(message, e, annotationMirror);
                }
            }
        }
    }

    protected void generateConfigFiles() {
        Filer filer = processingEnv.getFiler();
        log("Working on resource file: " + FACTORIES_FILE_PATH);
        Multimap<String, String> allServices = loadExistsServices(filer);

        for (String providerInterface : providers.keySet()) {
            Set<String> newServices = new HashSet<>(providers.get(providerInterface));
            Collection<String> extServices = allServices.get(providerInterface);
            if (extServices.containsAll(newServices)) {
                log("No new service entries being added for: " + providerInterface);
                continue;
            }
            allServices.putAll(providerInterface, newServices);
            log("New service file contents: " + allServices);
        }

        try {
            FileObject fileObject = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "", FACTORIES_FILE_PATH);
            try (OutputStream out = fileObject.openOutputStream()) {
                SpringFactoriesFile.writeServiceFile(allServices, out);
            }
            log("Wrote to: " + fileObject.toUri());
        } catch (IOException e) {
            fatalError("Unable to create " + FACTORIES_FILE_PATH + ", " + e);
        }
    }

    private Multimap<String, String> loadExistsServices(Filer filer) {
        Multimap<String, String> allServices = HashMultimap.create();
        try {
            // would like to be able to print the full path
            // before we attempt to get the resource in case the behavior
            // of filer.getResource does change to match the spec, but there's
            // no good way to resolve CLASS_OUTPUT without first getting a resource.
            FileObject existingFile = filer.getResource(
                    StandardLocation.CLASS_OUTPUT, "", FACTORIES_FILE_PATH);
            log("Looking for existing resource file at " + existingFile.toUri());
            Multimap<String, String> oldServices = SpringFactoriesFile
                    .readServiceFile(existingFile.openInputStream());
            log("Existing service entries: " + oldServices);
            allServices.putAll(oldServices);
        } catch (IOException e) {
            // According to the javadoc, Filer.getResource throws an exception
            // if the file doesn't already exist.  In practice this doesn't
            // appear to be the case.  Filer.getResource will happily return a
            // FileObject that refers to a non-existent file but will throw
            // IOException if you try to open an input stream for it.
            log("Resource file did not already exist.");
        }
        return allServices;
    }

    private boolean checkImplementer(
            TypeElement providerImplementer,
            TypeElement providerType,
            AnnotationMirror annotationMirror) {

        String verify = processingEnv.getOptions().get("verify");
        if (!Boolean.parseBoolean(verify)) {
            return true;
        }

        // : We're currently only enforcing the subtype relationship
        // constraint. It would be nice to enforce them all.

        Types types = processingEnv.getTypeUtils();

        if (types.isSubtype(providerImplementer.asType(), providerType.asType())) {
            return true;
        }

        // Maybe the provider has generic type, but the argument to @SpringFactory can't be generic.
        // So we allow that with a warning, which can be suppressed with @SuppressWarnings("rawtypes").
        // See https://github.com/google/auto/issues/870.
        if (types.isSubtype(providerImplementer.asType(), types.erasure(providerType.asType()))) {
            if (!rawTypesSuppressed(providerImplementer)) {
                warning("Service provider "
                                + providerType
                                + " is generic, so it can't be named exactly by @SpringFactory."
                                + " If this is OK, add @SuppressWarnings(\"rawtypes\").",
                        providerImplementer,
                        annotationMirror);
            }
            return true;
        }

        return false;
    }

    /**
     * Returns the contents of a {@code Class[]}-typed "value" field in a given {@code
     * annotationMirror}.
     */
    private ImmutableSet<DeclaredType> getValueFieldOfClasses(AnnotationMirror annotationMirror) {
        return getAnnotationValue(annotationMirror, "value")
                .accept(new SimpleAnnotationValueVisitor8<ImmutableSet<DeclaredType>, Void>() {
                            @Override
                            public ImmutableSet<DeclaredType> visitType(TypeMirror typeMirror, Void v) {
                                // (ronshapiro): class literals may not always be declared types, i.e.
                                // int.class, int[].class
                                return ImmutableSet.of(MoreTypes.asDeclared(typeMirror));
                            }

                            @Override
                            public ImmutableSet<DeclaredType> visitArray(
                                    List<? extends AnnotationValue> values, Void v) {
                                return values
                                        .stream()
                                        .flatMap(value -> value.accept(this, null).stream())
                                        .collect(toImmutableSet());
                            }
                        },
                        null);
    }
}
