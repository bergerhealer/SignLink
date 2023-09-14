package com.bergerkiller.bukkit.sl;

import org.bukkit.permissions.PermissionDefault;

import com.bergerkiller.bukkit.common.permissions.PermissionEnum;

public class Permission extends PermissionEnum {
    public static final Permission ADDSIGN = new Permission("addsign", PermissionDefault.OP, "Allows you to build signs containing variables");
    public static final Permission TOGGLEUPDATE = new Permission("toggleupdate", PermissionDefault.OP, "Allows you to set if signs are being updated or not");
    public static final Permission RELOAD = new Permission("reload", PermissionDefault.OP, "Allows you to reload the values.yml");
    public static final Permission SAVEALL = new Permission("saveall", PermissionDefault.OP, "Allows you to forcibly re-save the entire values.yml");
    public static final Permission EDIT = new Permission("edit", PermissionDefault.OP, "Allows you to edit all variables", 1);
    public static final Permission GLOBALDELETE = new Permission("globaldelete", PermissionDefault.OP, "Allows you to delete all variables from the server");
    public static final Permission REFRESHPAPI = new Permission("refreshpapi", PermissionDefault.OP, "Allows you to refresh/reload all PlaceholderAPI placeholder variables");

    private Permission(final String name, final PermissionDefault def, final String desc) {
        super("signlink." + name, def, desc);
    }

    private Permission(final String name, final PermissionDefault def, final String desc, final int argCount) {
        super("signlink." + name, def, desc, argCount);
    }
}
