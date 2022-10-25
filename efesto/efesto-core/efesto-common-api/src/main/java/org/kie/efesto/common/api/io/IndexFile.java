/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.efesto.common.api.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.kie.efesto.common.api.exceptions.KieEfestoCommonException;
import org.kie.efesto.common.api.utils.FileNameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.efesto.common.api.utils.FileNameUtils.getFileName;
import static org.kie.efesto.common.api.utils.MemoryFileUtils.getFileByFileNameFromClassloader;


/**
 * This specific <code>File</code> is meant to contain the index of the classes generated by a <b>compilation-plugin</b>
 */
public final class IndexFile extends File {

    public static final String INDEX_FILE = "IndexFile";
    public static final String FINAL_SUFFIX = "_json";
    private static final long serialVersionUID = -3471854812784089038L;

    private static final Logger logger = LoggerFactory.getLogger(IndexFile.class);


    private MemoryFile memoryFile;

    static String getIndexFileName(String modelType) {
        return String.format("%s.%s%s", INDEX_FILE, modelType, FINAL_SUFFIX);
    }

    static String validatePathName(String toValidate) {
        String fileName = getFileName(toValidate);
        if (!fileName.endsWith(FINAL_SUFFIX)) {
            throw new KieEfestoCommonException("Wrong file name " + fileName);
        }
        String model = getModel(fileName);
        if (model.isEmpty()) {
            throw new KieEfestoCommonException("Wrong file name " + fileName);
        }
        return toValidate;
    }

    static String getModel(String fileName) {
        return FileNameUtils.getSuffix(fileName).replace(FINAL_SUFFIX, "");
    }

    public IndexFile(String modelType) {
        super(validatePathName(getIndexFileName(modelType)));
        logger.debug("IndexFile {}", modelType);
        logger.debug(this.getAbsolutePath());
    }

    public IndexFile(String parent, String modelType) {
        super(parent, validatePathName(getIndexFileName(modelType)));
        logger.debug("IndexFile {} {}", parent, modelType);
        logger.debug(this.getAbsolutePath());
    }

    public IndexFile(File existingFile) {
        super(existingFile.toURI());
        logger.debug("IndexFile {}", existingFile);
        logger.debug(this.getAbsolutePath());
    }

    public IndexFile(MemoryFile memoryFile) {
        super(memoryFile.getName());
        this.memoryFile = memoryFile;
        logger.debug("IndexFile {}", memoryFile);
        logger.debug(this.getAbsolutePath());
        logger.debug("memoryFile {}", memoryFile.getAbsolutePath());
    }

    public String getModel() {
        return getModel(getSuffix());
    }

    @Override
    public long length() {
        return memoryFile != null ? memoryFile.length() : super.length();
    }

    public byte[] getContent() {
        return memoryFile != null ? memoryFile.getContent() : readContent();
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IndexFile) {
            if (equalsByExists((IndexFile) obj)) {
                if (this.exists()) {
                    return equalsByIsSameFile((IndexFile) obj);
                } else {
                    return Objects.equals(this.getAbsoluteFile().getAbsolutePath(), ((IndexFile)obj).getAbsoluteFile().getAbsolutePath());
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }

    private boolean equalsByExists(IndexFile toCompare) {
        return this.exists() == toCompare.exists();
    }

    private boolean equalsByIsSameFile(IndexFile toCompare) {
        try {
            return Files.isSameFile(this.toPath(), toCompare.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private byte[] readContent() {
        try(InputStream input = new FileInputStream(this)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int read;
            byte[] bytes = new byte[1024];
            while ((read = input.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            byte[] toReturn = out.toByteArray();
            out.flush();
            out.close();
            return toReturn;
        } catch (Exception e) {
            logger.warn("Failed to read content of {} ", this, e);
            return new byte[0];
        }
    }

    private String getSuffix() {
        return FileNameUtils.getSuffix(this.getName());
    }

    // TODO: For now, this method assumes only one or zero IndexFile per modelType will be found in classpath.
    //       If we find multiple IndexFiles of the same modelType, throw an Exception.
    //       In the future, we may merge them 
    public static Map<String, IndexFile> findIndexFilesFromClassLoader(ClassLoader classLoader, Set<String> modelTypes) {
        logger.debug("findAllIndexFilesFromClassLoader");
        Map<String, IndexFile> indexFileMap = new HashMap<>();
        for (String modelType : modelTypes) {
            IndexFile toSearch = new IndexFile(modelType);
            Optional<File> retrieved = getFileByFileNameFromClassloader(toSearch.getName(), classLoader);
            if (retrieved.isPresent()) {
                File actualFile = retrieved.get();
                IndexFile toReturn = actualFile instanceof MemoryFile ? new IndexFile((MemoryFile) actualFile) : new IndexFile(actualFile);
                logger.debug("found {}", toReturn);
                if (indexFileMap.containsKey(modelType)) {
                    throw new KieEfestoCommonException("Multiple IndexFiles for " + modelType + " found. " +
                                                       indexFileMap.get(modelType).getAbsolutePath() + ", " + toReturn.getAbsolutePath());
                }
                indexFileMap.put(modelType, toReturn);
            }
        }
        return indexFileMap;
    }
}
