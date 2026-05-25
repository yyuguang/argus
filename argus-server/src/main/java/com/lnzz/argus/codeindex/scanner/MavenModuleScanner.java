package com.lnzz.argus.codeindex.scanner;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @classname: MavenModuleScanner
 * @author: Fantasy
 * @date: 2026/05/19 16:55
 * @description: Maven 模块扫描器，基于 pom.xml 解析模块结构和父子关系。
 */
@Slf4j
public class MavenModuleScanner {

    private static final String BUILD_TYPE_MAVEN = "MAVEN";
    private static final String BUILD_TYPE_UNKNOWN = "UNKNOWN";
    private static final String DEFAULT_PACKAGING = "jar";
    private static final String UNKNOWN_PACKAGING = "unknown";

    /**
     * 扫描仓库 Maven 模块结构。
     *
     * @param repositoryRoot 仓库根目录
     * @return 模块扫描结果
     */
    public List<ModuleScanResult> scan(Path repositoryRoot) {
        if (repositoryRoot == null || !Files.isDirectory(repositoryRoot)) {
            return List.of();
        }
        Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
        Path rootPom = normalizedRoot.resolve("pom.xml");
        if (!Files.exists(rootPom)) {
            return List.of();
        }
        List<ModuleScanResult> results = new ArrayList<>();
        scanPom(normalizedRoot, normalizedRoot, null, results, new LinkedHashSet<>());
        return results;
    }

    private void scanPom(Path repositoryRoot, Path moduleRoot, String parentModulePath,
                         List<ModuleScanResult> results, Set<String> visitedModulePaths) {
        String modulePath = normalizeRelativePath(repositoryRoot, moduleRoot);
        if (!visitedModulePaths.add(modulePath)) {
            return;
        }
        Path pomPath = moduleRoot.resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            ModuleScanResult missingPomResult = buildUnknownModule(moduleRoot, modulePath, parentModulePath);
            missingPomResult.getWarnings().add("模块缺失 pom.xml，无法解析 Maven 坐标");
            results.add(missingPomResult);
            return;
        }
        try {
            Document document = parsePom(pomPath);
            Element project = document.getDocumentElement();
            ModuleScanResult result = new ModuleScanResult();
            result.setModuleName(resolveModuleName(project, moduleRoot, repositoryRoot));
            result.setModulePath(modulePath);
            result.setParentModulePath(parentModulePath);
            result.setBuildType(BUILD_TYPE_MAVEN);
            result.setPackaging(defaultIfBlank(directChildText(project, "packaging"), DEFAULT_PACKAGING));
            results.add(result);

            for (String childModule : directModuleNames(project)) {
                Path childRoot = moduleRoot.resolve(childModule).normalize();
                scanPom(repositoryRoot, childRoot, modulePath, results, visitedModulePaths);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.warn("解析 Maven pom.xml 失败: {}", pomPath, e);
            ModuleScanResult failedResult = buildUnknownModule(moduleRoot, modulePath, parentModulePath);
            failedResult.getWarnings().add("pom.xml 解析失败: " + e.getMessage());
            results.add(failedResult);
        }
    }

    private Document parsePom(Path pomPath) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream inputStream = Files.newInputStream(pomPath)) {
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            return document;
        }
    }

    private ModuleScanResult buildUnknownModule(Path moduleRoot, String modulePath, String parentModulePath) {
        ModuleScanResult result = new ModuleScanResult();
        result.setModuleName(moduleRoot.getFileName() == null ? "root" : moduleRoot.getFileName().toString());
        result.setModulePath(modulePath);
        result.setParentModulePath(parentModulePath);
        result.setBuildType(BUILD_TYPE_UNKNOWN);
        result.setPackaging(UNKNOWN_PACKAGING);
        return result;
    }

    private String resolveModuleName(Element project, Path moduleRoot, Path repositoryRoot) {
        String artifactId = directChildText(project, "artifactId");
        if (!isBlank(artifactId)) {
            return artifactId;
        }
        if (moduleRoot.equals(repositoryRoot)) {
            return moduleRoot.getFileName() == null ? "root" : moduleRoot.getFileName().toString();
        }
        return moduleRoot.getFileName() == null ? "module" : moduleRoot.getFileName().toString();
    }

    private List<String> directModuleNames(Element project) {
        List<String> modules = new ArrayList<>();
        Element modulesElement = directChild(project, "modules");
        if (modulesElement == null) {
            return modules;
        }
        NodeList children = modulesElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && "module".equals(childElement.getTagName())) {
                String moduleName = childElement.getTextContent();
                if (!isBlank(moduleName)) {
                    modules.add(moduleName.trim());
                }
            }
        }
        return modules;
    }

    private String directChildText(Element parent, String tagName) {
        Element child = directChild(parent, tagName);
        return child == null ? null : child.getTextContent();
    }

    private Element directChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && tagName.equals(childElement.getTagName())) {
                return childElement;
            }
        }
        return null;
    }

    private String normalizeRelativePath(Path repositoryRoot, Path path) {
        Path relativePath = repositoryRoot.relativize(path.toAbsolutePath().normalize());
        String normalized = relativePath.toString().replace('\\', '/');
        return ".".equals(normalized) ? "" : normalized;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
