package com.sanri.tools.modules.name.service;

import com.sanri.tools.modules.core.service.file.FileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.util.*;

@Component
@Slf4j
public class BizTranslate {
    @Autowired
    private FileManager fileManager;

    public static final String module = "translate";

    public void doTranslate(String[] bizs, TranslateCharSequence translateCharSequence) {
        if (bizs == null) return ;

        // 按顺序找到所有的业务词映射, 后面的覆盖前面的
        Properties properties = new Properties();
        for (String biz : bizs) {
            try {
                Properties mirrmorKeyValue = bizMirrmorKeyValue(biz);
                properties.putAll(mirrmorKeyValue);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 进行业务词翻译
        Set<String> needTranslateWords = translateCharSequence.getNeedTranslateWords();
        for (String needTranslateWord : needTranslateWords) {
            String bizMirror = Objects.toString(properties.get(needTranslateWord));
            if (StringUtils.isNotBlank(bizMirror)) {
                translateCharSequence.addTranslate(needTranslateWord, bizMirror);
                translateCharSequence.setTranslate(true, needTranslateWord);
            }
        }
    }

    public List<String> bizs() {
        return fileManager.simpleConfigNames(module);
    }

    public List<String> mirrors(String biz) throws IOException {
        String content = fileManager.readConfig(module, biz);
        return Arrays.asList(StringUtils.split(content,'\n'));
    }

    public Properties bizMirrmorKeyValue(String biz) throws IOException {
        String content = fileManager.readConfig(module, biz);
        InputStream inputStream = new StringBufferInputStream(content);
        Properties properties = new Properties();
        properties.load(inputStream);
        inputStream.close();
        return properties;
    }


    public void writeMirror(String biz, String content) throws IOException {
        fileManager.writeConfig(module,biz,content);
    }
}
