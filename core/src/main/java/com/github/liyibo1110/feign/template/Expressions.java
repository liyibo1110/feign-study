package com.github.liyibo1110.feign.template;

import com.github.liyibo1110.feign.Param;
import com.github.liyibo1110.feign.Util;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author liyibo
 * @date 2026-04-29 14:20
 */
public final class Expressions {
    private static final int MAX_EXPRESSION_LENGTH = 10000;
    private static final String PATH_STYLE_OPERATOR = ";";

    static final Pattern EXPRESSION_PATTERN = Pattern.compile("^(\\{([+#./;?&=,!@|]?)(.+)\\})$");

    private static final Pattern VARIABLE_LIST_PATTERN = Pattern.compile("(([\\w-\\[\\]$]|%[0-9A-Fa-f]{2})(\\.?([\\w-\\[\\]$]|%[0-9A-Fa-f]{2}))*(:.*|\\*)?)(,(([\\w-\\[\\]$]|%[0-9A-Fa-f]{2})(\\.?([\\w-\\[\\]$]|%[0-9A-Fa-f]{2}))*(:.*|\\*)?))*");

    public static Expression create(final String value) {
        /* remove the start and end braces */
        final String expression = stripBraces(value);
        if (expression == null || expression.isEmpty())
            throw new IllegalArgumentException("an expression is required.");

        /* Check if the expression is too long */
        if (expression.length() > MAX_EXPRESSION_LENGTH)
            throw new IllegalArgumentException("expression is too long. Max length: " + MAX_EXPRESSION_LENGTH);

        /* create a new regular expression matcher for the expression */
        String variableName = null;
        String variablePattern = null;
        String operator = null;
        Matcher matcher = EXPRESSION_PATTERN.matcher(value);
        if (matcher.matches()) {
            /* grab the operator */
            operator = matcher.group(2).trim();

            /* we have a valid variable expression, extract the name from the first group */
            variableName = matcher.group(3).trim();
            if (variableName.contains(":")) {
                /* split on the colon and ensure the size of parts array must be 2 */
                String[] parts = variableName.split(":", 2);
                variableName = parts[0];
                variablePattern = parts[1];
            }

            /* look for nested expressions */
            if (variableName.contains("{")) {
                /* nested, literal */
                return null;
            }
        }

        /* check for an operator */
        if (PATH_STYLE_OPERATOR.equalsIgnoreCase(operator))
            return new PathStyleExpression(variableName, variablePattern);

        /* default to simple */
        return SimpleExpression.isSimpleExpression(value)
                ? new SimpleExpression(variableName, variablePattern)
                : null; // Return null if it can't be validated as a Simple Expression -- Probably a Literal
    }

    private static String stripBraces(String expression) {
        if (expression == null)
            return null;

        if (expression.startsWith("{") && expression.endsWith("}"))
            return expression.substring(1, expression.length() - 1);

        return expression;
    }

    static class SimpleExpression extends Expression {

        private static final String DEFAULT_SEPARATOR = ",";
        protected String separator = DEFAULT_SEPARATOR;
        private boolean nameRequired = false;

        SimpleExpression(String name, String pattern) {
            super(name, pattern);
        }

        SimpleExpression(String name, String pattern, String separator, boolean nameRequired) {
            this(name, pattern);
            this.separator = separator;
            this.nameRequired = nameRequired;
        }

        protected String encode(Object value) {
            return UriUtils.encode(value.toString(), Util.UTF_8);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected String expand(Object variable, boolean encode) {
            StringBuilder expanded = new StringBuilder();
            if (Iterable.class.isAssignableFrom(variable.getClass())) {
                expanded.append(this.expandIterable((Iterable<?>) variable));
            } else if (Map.class.isAssignableFrom(variable.getClass())) {
                expanded.append(this.expandMap((Map<String, ?>) variable));
            } else if (Optional.class.isAssignableFrom(variable.getClass())) {
                Optional<?> optional = (Optional) variable;
                if (optional.isPresent()) {
                    expanded.append(this.expand(optional.get(), encode));
                } else {
                    if (!this.nameRequired)
                        return null;
                    expanded.append(this.encode(this.getName())).append("=");
                }
            } else {
                if (this.nameRequired)
                    expanded.append(this.encode(this.getName())).append("=");
                expanded.append((encode) ? encode(variable) : variable);
            }

            /* return the string value of the variable */
            String result = expanded.toString();
            if (!this.matches(result))
                throw new IllegalArgumentException("Value " + expanded + " does not match the expression pattern: " + this.getPattern());

            return result;
        }

        protected String expandIterable(Iterable<?> values) {
            StringBuilder result = new StringBuilder();
            for (Object value : values) {
                if (value == null) {
                    /* skip */
                    continue;
                }

                /* expand the value */
                String expanded = this.encode(value);
                if (expanded.isEmpty()) {
                    /* always append the separator */
                    result.append(this.separator);
                } else {
                    if (result.length() != 0) {
                        if (!result.toString().equalsIgnoreCase(this.separator))
                            result.append(this.separator);
                    }
                    if (this.nameRequired)
                        result.append(this.encode(this.getName())).append("=");

                    result.append(expanded);
                }
            }

            /* return the expanded value */
            return result.toString();
        }

        protected String expandMap(Map<String, ?> values) {
            StringBuilder result = new StringBuilder();

            for (Map.Entry<String, ?> entry : values.entrySet()) {
                StringBuilder expanded = new StringBuilder();
                String name = this.encode(entry.getKey());
                String value = this.encode(entry.getValue().toString());

                expanded.append(name).append("=");
                if (!value.isEmpty())
                    expanded.append(value);

                if (result.length() != 0)
                    result.append(this.separator);

                result.append(expanded);
            }
            return result.toString();
        }

        protected static boolean isSimpleExpression(String expressionCandidate) {
            final Matcher matcher = EXPRESSION_PATTERN.matcher(expressionCandidate);
            return matcher.matches()
                    && matcher.group(2).isEmpty() // Simple Expressions do not support any special operators
                    && VARIABLE_LIST_PATTERN.matcher(matcher.group(3)).matches();
        }
    }

    public static class PathStyleExpression extends SimpleExpression implements Param.Expander {

        public PathStyleExpression(String name, String pattern) {
            super(name, pattern, ";", true);
        }

        @Override
        protected String expand(Object variable, boolean encode) {
            return this.separator + super.expand(variable, encode);
        }

        @Override
        public String expand(Object value) {
            return this.expand(value, true);
        }

        @Override
        public String getValue() {
            if (this.getPattern() != null)
                return "{" + this.separator + this.getName() + ":" + this.getPattern() + "}";

            return "{" + this.separator + this.getName() + "}";
        }
    }
}
