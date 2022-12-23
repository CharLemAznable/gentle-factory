package com.github.charlemaznable.gentle.spring.factory.processor;

import com.github.charlemaznable.gentle.spring.factory.AutoConfigurationImport;
import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.github.charlemaznable.gentle.spring.factory.processor.AutoConfigurationImportsFile.IMPORTS_FILE_PATH;

@AutoService(Processor.class)
@SupportedOptions({"debug"})
public final class AutoConfigurationImportProcessor extends AbstractCommonProcessor {

    private final List<String> configurations = new ArrayList<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(AutoConfigurationImport.class.getName());
    }

    protected void processAnnotations(Set<? extends TypeElement> annotations,
                                      RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(AutoConfigurationImport.class);
        log(annotations.toString());
        log(elements.toString());

        for (Element e : elements) {
            // (gak): check for error trees?
            TypeElement providerImplementer = MoreElements.asType(e);
            log("configuration implementer: " + providerImplementer.getQualifiedName());
            configurations.add(getBinaryName(providerImplementer));
        }
    }

    protected void generateConfigFiles() {
        Filer filer = processingEnv.getFiler();
        log("Working on resource file: " + IMPORTS_FILE_PATH);
        List<String> allConfigurations = loadExistsServices(filer);
        if (allConfigurations.containsAll(configurations)) {
            log("No new configuration entries being added to: " + allConfigurations);
        } else {
            allConfigurations.removeAll(configurations);
            allConfigurations.addAll(configurations);
            log("New service file contents: " + allConfigurations);
        }

        try {
            FileObject fileObject = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "", IMPORTS_FILE_PATH);
            try (OutputStream out = fileObject.openOutputStream()) {
                AutoConfigurationImportsFile.writeServiceFile(allConfigurations, out);
            }
            log("Wrote to: " + fileObject.toUri());
        } catch (IOException e) {
            fatalError("Unable to create " + IMPORTS_FILE_PATH + ", " + e);
        }
    }

    private List<String> loadExistsServices(Filer filer) {
        List<String> allConfigurations = new ArrayList<>();
        try {
            // would like to be able to print the full path
            // before we attempt to get the resource in case the behavior
            // of filer.getResource does change to match the spec, but there's
            // no good way to resolve CLASS_OUTPUT without first getting a resource.
            FileObject existingFile = filer.getResource(
                    StandardLocation.CLASS_OUTPUT, "", IMPORTS_FILE_PATH);
            log("Looking for existing resource file at " + existingFile.toUri());
            List<String> oldConfigurations = AutoConfigurationImportsFile
                    .readServiceFile(existingFile.openInputStream());
            log("Existing configuration entries: " + oldConfigurations);
            allConfigurations.addAll(oldConfigurations);
        } catch (IOException e) {
            // According to the javadoc, Filer.getResource throws an exception
            // if the file doesn't already exist.  In practice this doesn't
            // appear to be the case.  Filer.getResource will happily return a
            // FileObject that refers to a non-existent file but will throw
            // IOException if you try to open an input stream for it.
            log("Resource file did not already exist.");
        }
        return allConfigurations;
    }
}
