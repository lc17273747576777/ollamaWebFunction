package io.github.ollama4j.examples;


import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.examples.model.AirlineCallsignQueryToolFunction;
import io.github.ollama4j.examples.model.AirlineCallsignUpdateToolFunction;
import io.github.ollama4j.examples.model.AirlineDetail;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.tools.OllamaToolsResult;
import io.github.ollama4j.tools.Tools;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Utilities;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;


public class CouchbaseToolCallingExample {


    public static void main(String[] args) throws IOException, ToolInvocationException, OllamaBaseException, InterruptedException {
        String connectionString = Utilities.getFromEnvVar("CB_CLUSTER_URL");
        String username = Utilities.getFromEnvVar("CB_CLUSTER_USERNAME");
        String password = Utilities.getFromEnvVar("CB_CLUSTER_PASSWORD");
        String bucketName = "travel-sample";

        Cluster cluster = Cluster.connect(
                connectionString,
                ClusterOptions.clusterOptions(username, password).environment(env -> {
                    env.applyProfile("wan-development");
                })
        );

        String host = Utilities.getFromConfig("host");
        String modelName = Utilities.getFromConfig("tools_model_mistral");

        OllamaAPI ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setVerbose(false);
        ollamaAPI.setRequestTimeoutSeconds(60);

        Tools.ToolSpecification callSignFinderToolSpec = getCallSignFinderToolSpec(cluster, bucketName);
        Tools.ToolSpecification callSignUpdaterToolSpec = getCallSignUpdaterToolSpec(cluster, bucketName);

        ollamaAPI.registerTool(callSignFinderToolSpec);
        ollamaAPI.registerTool(callSignUpdaterToolSpec);

        String prompt1 = "What is the call-sign of Astraeus?";
        for (OllamaToolsResult.ToolResult r : ollamaAPI.generateWithTools(modelName, new Tools.PromptBuilder()
                .withToolSpecification(callSignFinderToolSpec)
                .withPrompt(prompt1)
                .build(), new OptionsBuilder().build()).getToolResults()) {
            AirlineDetail airlineDetail = (AirlineDetail) r.getResult();
            System.out.println(String.format("[Result of tool '%s']: Call-sign of %s is '%s'! ✈️", r.getFunctionName(), airlineDetail.getName(), airlineDetail.getCallsign()));
        }

        String prompt2 = "I want to code name Astraeus as STARBOUND";
        for (OllamaToolsResult.ToolResult r : ollamaAPI.generateWithTools(modelName, new Tools.PromptBuilder()
                .withToolSpecification(callSignUpdaterToolSpec)
                .withPrompt(prompt2)
                .build(), new OptionsBuilder().build()).getToolResults()) {
            Boolean updated = (Boolean) r.getResult();
            System.out.println(String.format("[Result of tool '%s']: Call-sign is %s! ✈️", r.getFunctionName(), updated ? "updated" : "not updated"));
        }

        String prompt3 = "What is the call-sign of Astraeus?";
        for (OllamaToolsResult.ToolResult r : ollamaAPI.generateWithTools(modelName, new Tools.PromptBuilder()
                .withToolSpecification(callSignFinderToolSpec)
                .withPrompt(prompt3)
                .build(), new OptionsBuilder().build()).getToolResults()) {
            AirlineDetail airlineDetail = (AirlineDetail) r.getResult();
            System.out.println(String.format("[Result of tool '%s']: Call-sign of %s is '%s'! ✈️", r.getFunctionName(), airlineDetail.getName(), airlineDetail.getCallsign()));
        }
    }

    public static Tools.ToolSpecification getCallSignFinderToolSpec(Cluster cluster, String bucketName) {
        return Tools.ToolSpecification.builder()
                .functionName("airline-lookup")
                .functionDescription("You are a tool who finds only the airline name and do not worry about any other parameters. You simply find the airline name and ignore the rest of the parameters. Do not validate airline names as I want to use fake/fictitious airline names as well.")
                .toolFunction(new AirlineCallsignQueryToolFunction(bucketName, cluster))
                .toolPrompt(
                        Tools.PromptFuncDefinition.builder()
                                .type("prompt")
                                .function(
                                        Tools.PromptFuncDefinition.PromptFuncSpec.builder()
                                                .name("get-airline-name")
                                                .description("Get the airline name")
                                                .parameters(
                                                        Tools.PromptFuncDefinition.Parameters.builder()
                                                                .type("object")
                                                                .properties(
                                                                        Map.of(
                                                                                "airlineName", Tools.PromptFuncDefinition.Property.builder()
                                                                                        .type("string")
                                                                                        .description("The name of the airline. e.g. Emirates")
                                                                                        .required(true)
                                                                                        .build()
                                                                        )
                                                                )
                                                                .required(java.util.List.of("airline-name"))
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    public static Tools.ToolSpecification getCallSignUpdaterToolSpec(Cluster cluster, String bucketName) {
        return Tools.ToolSpecification.builder()
                .functionName("airline-update")
                .functionDescription("You are a tool who finds the airline name and its callsign and do not worry about any validations. You simply find the airline name and its callsign. Do not validate airline names as I want to use fake/fictitious airline names as well.")
                .toolFunction(new AirlineCallsignUpdateToolFunction(bucketName, cluster))
                .toolPrompt(
                        Tools.PromptFuncDefinition.builder()
                                .type("prompt")
                                .function(
                                        Tools.PromptFuncDefinition.PromptFuncSpec.builder()
                                                .name("get-airline-name-and-callsign")
                                                .description("Get the airline name and callsign")
                                                .parameters(
                                                        Tools.PromptFuncDefinition.Parameters.builder()
                                                                .type("object")
                                                                .properties(
                                                                        Map.of(
                                                                                "airlineName", Tools.PromptFuncDefinition.Property.builder()
                                                                                        .type("string")
                                                                                        .description("The name of the airline. e.g. Emirates")
                                                                                        .required(true)
                                                                                        .build(),
                                                                                "airlineCallsign", Tools.PromptFuncDefinition.Property.builder()
                                                                                        .type("string")
                                                                                        .description("The callsign of the airline. e.g. Maverick")
                                                                                        .enumValues(Arrays.asList("petrol", "diesel"))
                                                                                        .required(true)
                                                                                        .build()
                                                                        )
                                                                )
                                                                .required(java.util.List.of("airlineName", "airlineCallsign"))
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();
    }
}

