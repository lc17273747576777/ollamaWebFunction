package io.github.ollama4j.webui.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.models.chat.*;
import io.github.ollama4j.models.ps.ModelsProcessResponse;
import io.github.ollama4j.models.response.LibraryModel;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.tools.OllamaToolsResult;
import io.github.ollama4j.tools.Tools;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.webui.data.ModelItem;
import io.github.ollama4j.webui.data.ModelListItem;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

import io.github.ollama4j.models.generate.OllamaStreamHandler;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.webui.tools.specs.DatabaseQueryToolSpec;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatService implements Serializable {

    @Autowired
    private OllamaAPI ollamaAPI;
    private List<OllamaChatMessage> messages = new ArrayList<>();

    public void clearMessages() {
        messages.clear();
    }

    public List<LibraryModel> listLibraryModels() throws OllamaBaseException, IOException, URISyntaxException, InterruptedException {
        return ollamaAPI.listModelsFromLibrary();
    }

    public Collection<ModelItem> getModelItems()
            throws OllamaBaseException, IOException, URISyntaxException, InterruptedException {
        Collection<ModelItem> modelItems = new ArrayList<>(Collections.emptyList());
        ollamaAPI
                .listModels()
                .forEach(x -> modelItems.add(new ModelItem(x.getName(), x.getModelVersion())));
        return modelItems;
    }

    public Collection<ModelItem> getImageModelItems()
            throws OllamaBaseException, IOException, URISyntaxException, InterruptedException {
        Collection<ModelItem> modelItems = new ArrayList<>(Collections.emptyList());
        modelItems.add(new ModelItem("llava", "latest"));
        return modelItems;
    }

    public Collection<ModelListItem> getModels()
            throws OllamaBaseException, IOException, URISyntaxException, InterruptedException {
        Collection<Model> models = ollamaAPI.listModels();
        Collection<ModelListItem> modelListItems = new ArrayList<>();
        models.forEach(
                m -> {
                    DateTimeFormatter formatter =
                            DateTimeFormatter.ofPattern("dd MMM, yyyy hh:mm A").withZone(ZoneId.systemDefault());
                    modelListItems.add(
                            new ModelListItem(m.getModelName(), m.getModel(),
                                    m.getModifiedAt().format(formatter),
                                    m.getDigest(),
                                    FileUtils.byteCountToDisplaySize(m.getSize())));
                });
        return modelListItems;
    }

    public void ask(String message, String model, OllamaStreamHandler streamHandler) {
if (message.contains("【数据查询】")) {
            toolCallingDbQuery(message, model, streamHandler, "");
        } else {
            try {
                OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model);
                OllamaChatRequest ollamaChatRequestModel = builder.withMessages(messages).withMessage(OllamaChatMessageRole.USER, message).build();
                OllamaChatResult chat = ollamaAPI.chat(ollamaChatRequestModel, streamHandler);
                messages = chat.getChatHistory();
                chat.getResponse();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void askWithImages(
            String message, List<File> imageFiles, String model, OllamaStreamHandler streamHandler) {
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model);
        OllamaChatRequest ollamaChatRequestModel = builder
                .withMessages(messages)
                .withMessage(OllamaChatMessageRole.USER, message, null, imageFiles)
                .build();
        try {
            OllamaChatResult chat = ollamaAPI.chat(ollamaChatRequestModel, streamHandler);
            messages = chat.getChatHistory();
            chat.getResponse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isConnected() {
        try {
            return ollamaAPI.ping();
        } catch (Exception e) {

        }
        return false;
    }

    public ConnectionInfo getConnectionInfo() {
        try {
            return new ConnectionInfo("Connected", ollamaAPI.ps());
        } catch (IOException | InterruptedException | OllamaBaseException e) {
            // Ignored and status is just not available
        }
        return ConnectionInfo.NOT_AVAILABLE;
    }

    public void pullModel(String modelName)  {
        CompletableFuture.runAsync(() ->{
            try {
                ollamaAPI.pullModel(modelName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

    }

    public static class ConnectionInfo {

        public static final ConnectionInfo NOT_AVAILABLE = new ConnectionInfo("Not available", null);

        private final String status;
        private final ModelsProcessResponse ps;


        public ConnectionInfo(String statusText, ModelsProcessResponse ps) {
            this.status = statusText;
            this.ps = ps;
        }

        public boolean isAvailable() {
            return ps != null;
        }

        public List<ModelsProcessResponse.ModelProcess> getAvailableModels() {
            return ps.getModels();
        }

        public String getHost() {
            // TODO: OllamaAPI.host is private.
            return "localhost";
        }
    }

    public void toolCallingDbQuery(String message, String model, OllamaStreamHandler streamHandler, String dbUrl) {
        ollamaAPI.setVerbose(false);
        ollamaAPI.setRequestTimeoutSeconds(60);

        // Get the references to the tool specifications
        Tools.ToolSpecification databaseQueryToolSpecification = DatabaseQueryToolSpec.getSpecification();

        // Register the tool specifications
        ollamaAPI.registerTool(databaseQueryToolSpecification);

        // Use the fuel-price tool specifications in the prompt
        try {
            OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model);
            OllamaChatRequest ollamaChatRequestModel = builder.withMessages(messages).withMessage(OllamaChatMessageRole.USER, message).build();
//            OllamaChatResult chat = ollamaAPI.chat(ollamaChatRequestModel, streamHandler);
//            chat.getResponse();
//            messages = chat.getChatHistory();
//            chat.getResponse();
            // Use the database query tool specifications in the prompt
            for (OllamaToolsResult.ToolResult r : ollamaAPI.generateWithTools(model, new Tools.PromptBuilder()
                    .withToolSpecification(databaseQueryToolSpecification)
                    .withPrompt("Give me the details of the employee named 'Rahul Kumar'?")
                    .build(), new OptionsBuilder().build()).getToolResults()) {
//                OllamaChatResult chat = ollamaAPI.chat(ollamaChatRequestModel, streamHandler);
                streamHandler.accept(r.getResult().toString());
                System.out.printf("[Result of executing tool '%s']: %s%n", r.getFunctionName(), r.getResult().toString());
            }
        } catch (Exception e) {
            System.out.printf("failed" + e);
        }
    }

}
