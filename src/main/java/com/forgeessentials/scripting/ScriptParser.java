package com.forgeessentials.scripting;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

import org.apache.commons.lang3.StringUtils;

import com.forgeessentials.api.UserIdent;
import com.forgeessentials.permissions.core.ZonedPermissionHelper;
import com.forgeessentials.util.DoAsCommandSender;
import com.forgeessentials.util.output.LoggingHandler;

public class ScriptParser
{

    public static interface ScriptMethod
    {

        public boolean process(ICommandSender sender, String[] args);

        public String getHelp();

    }

    public static interface ScriptArgument
    {

        public String process(ICommandSender sender) throws ScriptException;

        public String getHelp();

    }

    private static final Pattern ARGUMENT_PATTERN = Pattern.compile("@(\\w+)(.*)");

    public static String[] processArguments(ICommandSender sender, String[] actionArgs, List<String> args)
    {
        for (int i = 0; i < actionArgs.length; i++)
        {
            Matcher matcher = ARGUMENT_PATTERN.matcher(actionArgs[i]);
            if (!matcher.matches())
                continue;
            String modifier = matcher.group(1).toLowerCase();
            String rest = matcher.group(2);

            ScriptArgument argument = ScriptArguments.get(modifier);
            if (argument != null)
            {
                actionArgs[i] = argument.process(sender) + rest;
            }
            else
            {
                try
                {
                    int idx = Integer.parseInt(modifier);
                    if (args == null || idx >= args.size())
                        throw new SyntaxException("Missing argument @%d", idx);
                    actionArgs[i] = args.get(idx) + rest;
                }
                catch (NumberFormatException e)
                {
                    throw new SyntaxException("Unknown argument modifier \"%s\"", modifier);
                }
            }
        }
        return actionArgs;
    }

    public static void run(List<String> script)
    {
        run(script, null);
    }

    public static void run(List<String> script, ICommandSender sender)
    {
        run(script, sender, null);
    }

    public static boolean run(List<String> script, ICommandSender sender, List<String> args)
    {
        for (String action : script)
            if (!run(action, sender, args))
                return false;
        return true;
    }

    public static boolean run(String action, ICommandSender sender, List<String> argumentValues)
    {
        String[] args = action.split(" ", 2);
        String cmd = args[0].toLowerCase();
        args = args.length > 1 ? args[1].split(" ") : new String[0];
        args = processArguments(sender, args, argumentValues);
        if (cmd.isEmpty())
            throw new SyntaxException("Could not handle script action \"%s\"", action);

        char c = cmd.charAt(0);
        switch (c)
        {
        case '/':
        case '$':
        case '?':
        case '*':
        {
            ICommandSender cmdSender = sender;
            if (cmd.equals("p") || cmd.equals("feperm"))
                cmdSender = MinecraftServer.getServer();

            boolean ignoreErrors = false;
            modifierLoop: while (true)
            {
                cmd = cmd.substring(1);
                switch (c)
                {
                case '$':
                    if (!(cmdSender instanceof DoAsCommandSender))
                        cmdSender = new DoAsCommandSender(ZonedPermissionHelper.SERVER_IDENT, sender);
                    ((DoAsCommandSender) cmdSender).setIdent(ZonedPermissionHelper.SERVER_IDENT);
                    break;
                case '?':
                    ignoreErrors = true;
                    break;
                case '*':
                    if (sender instanceof EntityPlayer)
                    {
                        if (!(cmdSender instanceof DoAsCommandSender))
                            cmdSender = new DoAsCommandSender(UserIdent.get((EntityPlayer) sender), sender);
                        ((DoAsCommandSender) cmdSender).setHideChatMessages(true);
                    }
                    break;
                case '/':
                    break modifierLoop;
                default:
                    throw new SyntaxException("Could not handle script action \"%s\"", action);
                }
                c = cmd.charAt(0);
            }
            ICommand mcCommand = (ICommand) MinecraftServer.getServer().getCommandManager().getCommands().get(cmd);
            try
            {
                mcCommand.processCommand(cmdSender, args);
            }
            catch (CommandException e)
            {
                if (!ignoreErrors)
                    throw e;
                LoggingHandler.felog.info(String.format("Silent script command /%s %s failed: %s", cmd, StringUtils.join(args, " "), e.getMessage()));
            }
            return true;
        }
        default:
            boolean canFail = false;
            if (cmd.length() > 1 && cmd.charAt(0) == '?')
            {
                canFail = true;
                cmd = cmd.substring(1);
            }
            ScriptMethod method = ScriptMethods.get(cmd);
            if (method == null)
                throw new SyntaxException("Unknown script method \"%s\"", cmd);
            return method.process(sender, args) | canFail;
        }
    }

    public static class ScriptException extends RuntimeException
    {

        public ScriptException()
        {
            super();
        }

        public ScriptException(String message)
        {
            super(message);
        }

        public ScriptException(String message, Object... args)
        {
            super(String.format(message, args));
        }

    }

    public static class SyntaxException extends ScriptException
    {

        public SyntaxException(String message, Object... args)
        {
            super(message, args);
        }

    }

    public static class MissingPlayerException extends ScriptException
    {

        public MissingPlayerException()
        {
            super("Missing player for @player argument");
        }

    }

    public static class MissingPermissionException extends ScriptException
    {

        public final String permission;

        public MissingPermissionException(String permission, String message)
        {
            super(message);
            this.permission = permission;
        }

        public MissingPermissionException(String permission, String message, Object... args)
        {
            super(message, args);
            this.permission = permission;
        }

    }

}
