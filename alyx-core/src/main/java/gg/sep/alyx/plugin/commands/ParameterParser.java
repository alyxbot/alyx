package gg.sep.alyx.plugin.commands;

import net.dv8tion.jda.api.events.Event;

import gg.sep.alyx.plugin.CommandParseException;

/**
 * Represents a class which is able to parse a string into the given type {@code T}.
 * @param <T> Type of the output once the input string is parsed with {@link ParameterParser#parse}.
 */
public interface ParameterParser<T> {
    /**
     * Returns the class of {@code T}.
     * @return The output class of this parser.
     */
    Class<T> getType();

    /**
     * Parses the given input string into the parser's type.
     *
     * @param value Input string value to parse.
     * @param event The event which triggered the command.
     * @return String value parsed into the parser's type.
     * @throws CommandParseException Thrown if parsing fails.
     */
    T parse(String value, Event event) throws CommandParseException;
}
