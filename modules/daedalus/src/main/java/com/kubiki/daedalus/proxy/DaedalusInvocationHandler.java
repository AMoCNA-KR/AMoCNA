package com.kubiki.daedalus.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.annotation.Bind;
import com.kubiki.daedalus.annotation.Template;
import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.annotation.Type;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.TemplateParser;
import com.kubiki.daedalus.core.TemplateToken;
import com.kubiki.daedalus.exception.DaedalusException;
import com.kubiki.daedalus.exception.HydrationException;
import com.kubiki.daedalus.exception.TemplateMappingException;
import com.kubiki.daedalus.exception.TemplateResourceException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kubiki.daedalus.core.DaedalusConstants.*;

public class DaedalusInvocationHandler implements InvocationHandler {
    private final Class<?> interfaceClass;
    private final Map<Method, List<TemplateToken>> methodTemplates = new HashMap<>();
    private final GlobalTemplateContext globalContext;
    private final Formatter formatter = new Formatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DaedalusInvocationHandler(Class<?> interfaceClass, GlobalTemplateContext globalContext) {
        this.interfaceClass = interfaceClass;
        this.globalContext = globalContext;
        TemplateParser parser = new TemplateParser();
        for (Method method : interfaceClass.getMethods()) {
            Template templateAnn = method.getAnnotation(Template.class);
            if (templateAnn != null) {
                String raw = loadResource(templateAnn.resource());
                methodTemplates.put(method, parser.parse(raw));
            }
        }
    }

    private String loadResource(String path) {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new TemplateResourceException("Resource not found: " + path);
            return new String(is.readAllBytes());
        } catch (Exception e) { 
            throw new TemplateResourceException("Failed to load template: " + path, e); 
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<TemplateToken> tokens = methodTemplates.get(method);
        if (tokens == null) {
            if (method.getName().equals(EQUALS_METHOD)) {
                if (args[0] == null) return false;
                if (!Proxy.isProxyClass(args[0].getClass())) return false;
                InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
                return this.equals(handler);
            }
            if (method.getName().equals(HASH_CODE_METHOD)) {
                return System.identityHashCode(proxy);
            }
            if (method.getName().equals(TO_STRING_METHOD)) {
                return PROXY_PREFIX + interfaceClass.getSimpleName() + PROXY_SUFFIX;
            }
            throw new DaedalusException("Method not annotated with @Template: " + method.getName());
        }

        Map<String, String> values = new HashMap<>(globalContext.getAll());
        Parameter[] params = method.getParameters();
        if (args != null) {
            for (int i = 0; i < params.length; i++) {
                Bind bind = params[i].getAnnotation(Bind.class);
                if (bind != null) {
                    Type typeAnn = params[i].getAnnotation(Type.class);
                    TemplateType type = (typeAnn != null) ? typeAnn.value() : TemplateType.LITERAL;
                    values.put(bind.value(), formatter.format(args[i], type));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (TemplateToken token : tokens) {
            if (token instanceof TemplateToken.StaticToken s) {
                sb.append(s.text());
            } else if (token instanceof TemplateToken.VariableToken v) {
                String val = values.get(v.name());
                if (val == null) {
                    val = v.defaultValue();
                }
                if (val == null) {
                    throw new HydrationException("Missing variable: " + v.name() + " for method: " + method.getName());
                }
                sb.append(val);
            }
        }

        String hydrated = sb.toString();
        if (method.getReturnType().equals(String.class)) return hydrated;
        try {
            return objectMapper.readValue(hydrated, method.getReturnType());
        } catch (Exception e) {
            throw new TemplateMappingException("Failed to map hydrated template to " + method.getReturnType().getSimpleName(), e);
        }
    }
}
