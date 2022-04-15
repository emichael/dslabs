/*
 * Copyright (c) 2018 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework.testing.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Result;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.search.SearchState;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import static dslabs.framework.testing.utils.Json.TYPE_FIELD_NAME;

/**
 * Utility class for serializing and deserializing various objects to JSON. Used
 * to interact with Oddity. Any {@link MessageEnvelope}, {@link TimerEnvelope},
 * or {@link SearchState} JSON'd by this class is assigned an ID and cached.
 * Responses from Oddity will reference this ID, and the {@link
 * dslabs.framework.testing.visualization.VizClient} can use the provided
 * methods to retrieve them.
 *
 * <p>All objects included in a {@link SearchState} (including the State
 * itself) can be JSON'd. (However, {@link SearchState} itself is serialized
 * through a custom serialization method in this class.) The JSON library will
 * ignore {@code transient} (and {@code static}) fields. This behavior is
 * congruent with the behavior of the {@link Cloning} utility class.
 *
 * <p>DSLabs classes, however, are not deserialized with JSON. All responses
 * from Oddity are simple messages with IDs that reference cached objects
 * previously sent.
 *
 * <p>The behavior JSON serialization can be customized through {@link
 * com.fasterxml.jackson.annotation.JsonIgnore} and related annotations, as well
 * as through custom serializers. Custom serializers can be registered in this
 * utility class or on the class directly with {@link com.fasterxml.jackson.databind.annotation.JsonSerialize}.
 * Students may need to write custom serializers if their objects contain
 * circular references. This caveat is described in the handout README.
 *
 * @see dslabs.framework.testing.visualization.VizClient
 * @see Cloning
 */
public abstract class Json {
    private static final String MESSAGE_PREFIX = "m", TIMER_PREFIX = "t",
            STATE_PREFIX = "s";
    static final String ID_FIELD_NAME = "@id";
    static final String TYPE_FIELD_NAME = "@type";

    private static final ObjectMapper rootMapper;

    private static final BiMap<MessageEnvelope, String> messageIDs =
            HashBiMap.create();
    private static final BiMap<TimerEnvelope, String> timerIDs =
            HashBiMap.create();
    private static final BiMap<SearchState, String> stateIDs =
            HashBiMap.create();

    static {
        rootMapper = new ObjectMapper();

        // Register standard module extensions
        rootMapper.registerModule(new GuavaModule());
        rootMapper.registerModule(new Jdk8Module());
        rootMapper.registerModule(new JavaTimeModule());

        // Serialize objects by fields, not methods
        rootMapper.setVisibility(rootMapper.getSerializationConfig()
                                           .getDefaultVisibilityChecker()
                                           .withFieldVisibility(Visibility.ANY)
                                           .withGetterVisibility(
                                                   Visibility.NONE)
                                           .withIsGetterVisibility(
                                                   Visibility.NONE));

        // Don't fail on "empty" objects
        rootMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Serialize the types of Commands and Results
        rootMapper.setAnnotationIntrospector(new AnnotationIntrospector());
    }

    public static String getMessageId(MessageEnvelope me) {
        if (!messageIDs.containsKey(me)) {
            messageIDs.put(me, MESSAGE_PREFIX + messageIDs.size());
        }
        return messageIDs.get(me);
    }

    public static MessageEnvelope getMessage(String id) {
        return messageIDs.inverse().get(id);
    }

    public static String getTimerId(TimerEnvelope te) {
        if (!timerIDs.containsKey(te)) {
            timerIDs.put(te, TIMER_PREFIX + timerIDs.size());
        }
        return timerIDs.get(te);
    }

    public static TimerEnvelope getTimer(String id) {
        return timerIDs.inverse().get(id);
    }

    public static String getStateId(SearchState s) {
        if (!stateIDs.containsKey(s)) {
            stateIDs.put(s, STATE_PREFIX + stateIDs.size());
        }
        return stateIDs.get(s);
    }

    public static SearchState getState(String id) {
        return stateIDs.inverse().get(id);
    }

    public static String toJson(SearchState object) {
        return toJson(object, null);
    }

    @SneakyThrows(JsonProcessingException.class)
    public static String toJson(SearchState object, StatePredicate invariant) {

        ObjectMapper stateMapper = rootMapper.copy();
        SimpleModule stateModule = new SimpleModule();
        stateModule.addSerializer(SearchState.class,
                new SingleStateSerializer(invariant));
        stateMapper.registerModule(stateModule);
        return stateMapper.writeValueAsString(object);
    }

    @SneakyThrows(IOException.class)
    public static JsonNode fromJson(String json) {
        return rootMapper.readTree(json);
    }

    @SneakyThrows(JsonProcessingException.class)
    public static String jsonTrace(SearchState state, StatePredicate invariant,
                                   String traceName, int traceId) {
        ObjectMapper jsonTraceMapper = rootMapper.copy();
        SimpleModule traceModule = new SimpleModule();
        traceModule.addSerializer(SearchState.class,
                new TraceSerializer(traceName, traceId, invariant));
        jsonTraceMapper.registerModule(traceModule);

        return jsonTraceMapper.writeValueAsString(state);
    }
}


@RequiredArgsConstructor
class SingleStateSerializer extends JsonSerializer<SearchState> {
    private final StatePredicate invariant;

    private void serializeMessage(MessageEnvelope me, JsonGenerator gen,
                                  SerializerProvider ser) throws IOException {
        gen.writeStartObject();
        gen.writeStringField(Json.ID_FIELD_NAME, Json.getMessageId(me));
        gen.writeStringField("from", me.from().rootAddress().toString());
        gen.writeStringField("to", me.to().rootAddress().toString());
        gen.writeStringField("type", me.message().getClass().getSimpleName());
        gen.writeObjectField("body", me.message());
        gen.writeEndObject();
    }

    private void serializeTimer(TimerEnvelope te, JsonGenerator gen,
                                SerializerProvider ser) throws IOException {
        gen.writeStartObject();
        gen.writeStringField(Json.ID_FIELD_NAME, Json.getTimerId(te));
        gen.writeStringField("to", te.to().rootAddress().toString());
        gen.writeStringField("type", te.timer().getClass().getSimpleName());
        gen.writeObjectField("body", te.timer());
        gen.writeEndObject();
    }

    @Override
    public void serialize(SearchState state, JsonGenerator gen,
                          SerializerProvider ser) throws IOException {
        SearchState s = state;

        gen.writeStartObject();
        gen.writeStringField(Json.ID_FIELD_NAME, Json.getStateId(s));

        // Write out the states of all servers
        gen.writeFieldName("states");
        gen.writeStartObject();
        for (Address a : s.addresses()) {
            gen.writeObjectField(a.toString(), s.node(a));
        }
        gen.writeEndObject();

        // Write out the previous event
        if (s.previousEvent() != null && s.previousEvent().isMessage()) {
            gen.writeFieldName("deliver-message");
            serializeMessage(s.previousEvent().message(), gen, ser);

        } else if (s.previousEvent() != null && s.previousEvent().isTimer()) {
            TimerEnvelope te = s.previousEvent().timer();

            gen.writeFieldName("deliver-timeout");
            serializeTimer(te, gen, ser);

            // By default, delivered timers are also cleared (could change)
            gen.writeFieldName("cleared-timeouts");
            gen.writeStartArray();
            serializeTimer(te, gen, ser);
            gen.writeEndArray();
        }

        // Write out the sent messages and set timers
        if (!s.newMessages().isEmpty()) {
            gen.writeFieldName("send-messages");
            gen.writeStartArray();
            for (MessageEnvelope me : s.newMessages()) {
                serializeMessage(me, gen, ser);
            }
            gen.writeEndArray();
        }
        if (!s.newTimers().isEmpty()) {
            gen.writeFieldName("set-timeouts");
            gen.writeStartArray();
            for (TimerEnvelope te : s.newTimers()) {
                serializeTimer(te, gen, ser);
            }
            gen.writeEndArray();
        }

        // Test if invariant is violated
        if (invariant != null && !invariant.test(s)) {
            gen.writeBooleanField("invariant-violated", true);
            gen.writeStringField("invariant-name", invariant.name());
            String detail = invariant.detail(state);
            if (detail != null) {
                gen.writeStringField("invariant-detail", detail);
            }
        } else {
            gen.writeBooleanField("invariant-violated", false);
        }

        gen.writeEndObject();
    }
}


@RequiredArgsConstructor
class TraceSerializer extends JsonSerializer<SearchState> {
    private final String traceName;
    private final int traceId;
    private final StatePredicate invariant;

    @Override
    public void serialize(SearchState terminalState, JsonGenerator gen,
                          SerializerProvider ser) throws IOException {
        // Write the basic metadata
        gen.writeStartObject();
        gen.writeObjectField("name", traceName);
        gen.writeObjectField("id", traceId);

        Set<Address> servers = new HashSet<>();

        JsonSerializer<SearchState> singleStateSerializer =
                new SingleStateSerializer(invariant);

        // Write the trace
        gen.writeFieldName("trace");
        gen.writeStartArray();
        for (SearchState s : terminalState.trace()) {
            for (Address a : s.addresses()) {
                servers.add(a);
            }
            singleStateSerializer.serialize(s, gen, ser);
        }
        gen.writeEndArray();

        // Finally write the servers array (last)
        gen.writeFieldName("servers");
        gen.writeStartArray();
        for (Address a : servers) {
            gen.writeString(a.toString());
        }
        gen.writeEndArray();
    }
}


class AnnotationIntrospector extends JacksonAnnotationIntrospector {
    @Override
    public TypeResolverBuilder<?> findTypeResolver(MapperConfig<?> config,
                                                   AnnotatedClass ann,
                                                   JavaType baseType) {
        if (Command.class.isAssignableFrom(baseType.getRawClass()) ||
                Result.class.isAssignableFrom(baseType.getRawClass())) {

            TypeIdResolver idRes = config.typeIdResolverInstance(ann,
                    SimpleNameResolver.class);
            idRes.init(baseType);

            return _constructStdTypeResolverBuilder().init(Id.CUSTOM, idRes)
                                                     .inclusion(As.PROPERTY)
                                                     .typeProperty(
                                                             TYPE_FIELD_NAME)
                                                     .typeIdVisibility(false);
        }

        return super.findTypeResolver(config, ann, baseType);
    }
}


class SimpleNameResolver extends TypeIdResolverBase {
    @Override
    public void init(JavaType baseType) {
    }

    @Override
    public Id getMechanism() {
        return Id.CUSTOM;
    }

    @Override
    public String idFromValue(Object obj) {
        return idFromValueAndType(obj, obj.getClass());
    }

    @Override
    public String idFromValueAndType(Object obj, Class<?> subType) {
        return subType.getSimpleName();
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id)
            throws IOException {
        throw new IOException("Can't use this resolver to deserialize.");
    }
}
