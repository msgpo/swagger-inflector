package io.swagger.oas.inflector.converters;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.oas.inflector.utils.ReflectionUtils;
import io.swagger.oas.inflector.validators.ValidationError;
import io.swagger.oas.inflector.validators.ValidationMessage;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class DefaultConverter extends ReflectionUtils implements Converter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConverter.class);

    private Map<String, Schema> definitions;

    public DefaultConverter(){}

    public Object convert(List<String> value, Parameter parameter, Class<?> cls, Map<String, Schema> definitions, Iterator<Converter> chain) throws ConversionException {
        return coerceValue(value, parameter, cls);
    }

    public Object convert(List<String> value, RequestBody body, Class<?> cls, Map<String, Schema> definitions, Iterator<Converter> chain) throws ConversionException {
        return coerceValue(value, body, cls);
    }

    public Object convert(List<String> value, RequestBody body, Class<?> cls, Class<?> innerClass, Map<String, Schema> definitions, Iterator<Converter> chain) throws ConversionException {
        return coerceValue(value, body, cls, innerClass);
    }

    public Object coerceValue(List<String> arguments, RequestBody body, Class<?> cls) throws ConversionException {
        return coerceValue(arguments, body, cls, null);
    }

    public Object coerceValue(List<String> arguments, RequestBody body, Class<?> cls, Class<?> innerClass) throws ConversionException {
        if (arguments == null || arguments.size() == 0) {
            return null;
        }

        LOGGER.debug("casting `" + arguments + "` to " + cls);
        if (List.class.equals(cls)) {
            if (isJson(arguments) && innerClass != null) {
                final List<Object> objects = new ArrayList<>();
                for (final String argument : arguments) {
                    final String[] split = argument.split("},");
                    for (final String aSplit : split) {
                        try {
                            final String object = aSplit.endsWith("}") ? aSplit : aSplit + "}";
                            objects.add(new ObjectMapper().readValue(object, innerClass));
                        } catch (IOException e) {
                            LOGGER.error("error casting `" + arguments + "` to " + cls);
                        }
                    }
                }
                return objects;

            } else if (body.getContent() != null) {
                for (String mediaType: body.getContent().keySet()) {
                    MediaType media = body.getContent().get(mediaType);
                    if (media.getSchema() != null) {
                        List<Object> output = new ArrayList<>();
                        if (media.getSchema() instanceof ArraySchema) {
                            ArraySchema arraySchema = ((ArraySchema) media.getSchema());
                            Schema inner = arraySchema.getItems();
                            // Ask what to do?
                        }

                        List<String> allStrings = new ArrayList<>();

                        for(final String argument: arguments) {
                            String[] split = argument.split(",");
                            List<String> strings = Arrays.asList(split);
                            if (strings.size() > 0) {
                                allStrings.addAll(strings);
                            }
                        }

                        return allStrings;
                    }
                }
            }
        } else if (isJson(arguments)) {
            try {
                return new ObjectMapper().readValue(arguments.get(0), cls);
            } catch (IOException e) {
                LOGGER.error("error casting `" + arguments + "` to " + cls);
            }

        } else if (body.getContent() != null) {
            for (String mediaType: body.getContent().keySet()) {
                MediaType media = body.getContent().get(mediaType);
                if (media.getSchema() != null) {
                    TypeFactory tf = Json.mapper().getTypeFactory();
                    return cast(arguments.get(0), media.getSchema(), tf.constructType(cls));
                }
            }
        }
        return null;
    }

    private boolean isJson (List<String> arguments) {
        boolean isJson = false;
        try {
            final ObjectMapper mapper = new ObjectMapper();
            for (String argument : arguments) {
                mapper.readTree(argument);
                isJson = true;
            }
        } catch (IOException e) {
            return false;
        }
        return isJson;
    }

    public Object coerceValue(List<String> arguments, Parameter parameter, Class<?> cls) throws ConversionException {
        if (arguments == null || arguments.size() == 0) {
            return null;
        }

        LOGGER.debug("casting `" + arguments + "` to " + cls);
        if (List.class.equals(cls)) {
            if (parameter.getSchema() != null) {
                List<Object> output = new ArrayList<>();
                if (parameter.getSchema() instanceof ArraySchema) {
                    ArraySchema arraySchema = ((ArraySchema) parameter.getSchema());
                    Schema inner = arraySchema.getItems();


                    // TODO: this does not need to be done this way, update the helper method
                    Parameter innerParam = new QueryParameter();
                    innerParam.setSchema(inner);
                    JavaType innerClass = getTypeFromParameter(innerParam, definitions);
                    for (String obj : arguments) {
                        String[] parts = new String[0];

                        if (Parameter.StyleEnum.FORM.equals(parameter.getStyle()) && !StringUtils.isEmpty(obj) && parameter.getExplode() == false ) {
                            parts = obj.split(",");
                        }
                        if (Parameter.StyleEnum.PIPEDELIMITED.equals(parameter.getStyle()) && !StringUtils.isEmpty(obj)) {
                            parts = obj.split("|");
                        }
                        if (Parameter.StyleEnum.SPACEDELIMITED.equals(parameter.getStyle()) && !StringUtils.isEmpty(obj)) {
                            parts = obj.split(" ");
                        }
                        if (Parameter.StyleEnum.FORM.equals(parameter.getStyle()) && !StringUtils.isEmpty(obj) && parameter.getExplode() == true) {
                            parts = new String[1];
                            parts[0]= obj;
                        }
                        for (String p : parts) {
                            Object ob = cast(p, inner, innerClass);
                            if (ob != null) {
                                output.add(ob);
                            }
                        }
                    }
                }
                return output;
            }
        } else if (parameter.getSchema() != null) {
            TypeFactory tf = Json.mapper().getTypeFactory();

            return cast(arguments.get(0), parameter.getSchema(), tf.constructType(cls));

        }
        return null;
    }

    public Object cast(List<String> arguments, Parameter parameter, JavaType javaType, Map<String, Schema> definitions) throws ConversionException {
        if (arguments == null || arguments.size() == 0) {
            return null;
        }
        Class<?> cls = javaType.getRawClass();

        LOGGER.debug("converting array `" + arguments + "` to `" + cls + "`");
        if (javaType.isArrayType()) {
            if (parameter.getSchema() != null) {
                List<Object> output = new ArrayList<>();
                if (parameter.getSchema() instanceof ArraySchema) {
                    ArraySchema arraySchema = (ArraySchema) parameter.getSchema();
                    if (arraySchema.getItems() != null) {
                        Schema inner = arraySchema.getItems();

                        // TODO: this does not need to be done this way, update the helper method
                        Parameter innerParam = new QueryParameter().schema(inner);
                        JavaType innerClass = getTypeFromParameter(innerParam, definitions);
                        for (String obj : arguments) {
                            String[] parts = new String[0];
                            CSVFormat format = null;
                            if (Parameter.StyleEnum.FORM.equals(parameter.getStyle()) && !StringUtils.isEmpty(obj) && parameter.getExplode() == false) {
                                format = CSVFormat.DEFAULT;
                            } else if (Parameter.StyleEnum.PIPEDELIMITED.equals(parameter.getStyle()) && !StringUtils.isEmpty(obj)) {
                                format = CSVFormat.newFormat('|').withQuote('"');
                            } else if (Parameter.StyleEnum.SPACEDELIMITED.equals(parameter.getStyle()) && !StringUtils.isEmpty(obj)) {
                                format = CSVFormat.newFormat(' ').withQuote('"');
                            }
                            if (format != null) {
                                try {
                                    for (CSVRecord record : CSVParser.parse(obj, format).getRecords()) {
                                        List<String> it = new ArrayList<String>();
                                        for (Iterator<String> x = record.iterator(); x.hasNext(); ) {
                                            it.add(x.next());
                                        }
                                        parts = it.toArray(new String[it.size()]);
                                    }
                                } catch (IOException e) {
                                }
                            } else {
                                parts = new String[1];
                                parts[0] = obj;
                            }
                            for (String p : parts) {
                                Object ob = cast(p, inner, innerClass);
                                if (ob != null) {
                                    output.add(ob);
                                }
                            }

                        }

                        return output;
                    }
                }
            }
        } else if (parameter != null) {
            return cast(arguments.get(0), parameter.getSchema(), javaType);
        }
        return null;
    }

    public Object cast(String argument, Schema property, JavaType javaType) throws ConversionException {
        if (argument == null || javaType == null) {
            return null;
        }
        Class<?> cls = javaType.getRawClass();
        LOGGER.debug("coercing `" + argument + "` to `" + cls + "`");
        try {
            if (Integer.class.equals(cls)) {
                return Integer.parseInt(argument);
            }
            if (Long.class.equals(cls)) {
                return Long.parseLong(argument);
            }
            if (Float.class.equals(cls)) {
                return Float.parseFloat(argument);
            }
            if (Double.class.equals(cls)) {
                return Double.parseDouble(argument);
            }
            if (String.class.equals(cls)) {
                return argument;
            }
            if (Boolean.class.equals(cls)) {
                if ("1".equals(argument)) {
                    return Boolean.TRUE;
                }
                if ("0".equals(argument)) {
                    return Boolean.FALSE;
                }
                return Boolean.parseBoolean(argument);
            }
            if (UUID.class.equals(cls)) {
                return UUID.fromString(argument);
            }
            if(LocalDate.class.equals(cls)) {
                return LocalDate.parse(argument);
            }
            if(DateTime.class.equals(cls)) {
                return DateTime.parse(argument);
            }
        } catch (NumberFormatException e) {
            LOGGER.debug("couldn't coerce `" + argument + "` to type " + cls);
            throw new ConversionException()
              .message(new ValidationMessage()
                .code(ValidationError.INVALID_FORMAT)
                .message("couldn't convert `" + argument + "` to type `" + cls + "`"));
        } catch (IllegalArgumentException e) {
            LOGGER.debug("couldn't coerce `" + argument + "` to type " + cls);
            throw new ConversionException()
              .message(new ValidationMessage()
                .code(ValidationError.INVALID_FORMAT)
                .message("couldn't convert `" + argument + "` to type `" + cls + "`"));
        }
        return null;
    }
}
