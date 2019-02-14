package com.gmail.nossr50.core.config;

import com.gmail.nossr50.core.McmmoCore;
import com.google.common.io.Files;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.yaml.snakeyaml.DumperOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Handles loading and cacheing configuration settings from a configurable compatible config file
 */
//@ConfigSerializable
public abstract class ConfigurableLoader implements DefaultKeys, VersionedConfig {

    /* PATH VARS */

    public final File DIRECTORY_DATA_FOLDER; //Directory that the file is in
    public final String FILE_RELATIVE_PATH; //Relative Path to the file
    protected final String DIRECTORY_DEFAULTS = "defaults";

    /* LOADERS */

    private YAMLConfigurationLoader defaultCopyLoader;
    private YAMLConfigurationLoader userCopyLoader;

    /* CONFIG FILES */

    private File resourceConfigCopy; //Copy of the default config from the JAR (file is copied so that admins can easily compare to defaults)
    private File resourceUserCopy; //File in the /$MCMMO_ROOT/mcMMO/ directory that may contain user edited settings

    /* ROOT NODES */

    private ConfigurationNode userRootNode = null;
    private ConfigurationNode defaultRootNode = null;

    /* CONFIG MANAGER */
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    //TODO: Needed?
    //private ConfigurationLoader<CommentedConfigurationNode> configManager;

    public ConfigurableLoader(File pathToDataFolder, String relativePath) {
        /*
         * These must be at the top
         */
        mkdirDefaults(); // Make our default config dir
        DIRECTORY_DATA_FOLDER = pathToDataFolder; //Data Folder for our plugin
        FILE_RELATIVE_PATH = relativePath; //Relative path to config from a parent folder

        //Attempt IO Operations
        try {
            //Makes sure we have valid Files corresponding to this config
            initConfigFiles();

            //Init Config Loaders
            initConfigLoaders();

            //Load Config Nodes
            loadConfig();

            //Attempt to update user file, and then load it into memory
            readConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
         * Print Errors about Keys
         */

        List<String> validKeyErrors = validateKeys(); // Validate Keys

        if(validKeyErrors != null && validKeyErrors.size() > 0)
        {
            for(String error : validKeyErrors)
            {
                McmmoCore.getLogger().severe(error);
            }
        }
    }

    /**
     * Initializes the default copy File and the user config File
     * @throws IOException
     */
    private void initConfigFiles() throws IOException {
        //Init our config copy
        resourceConfigCopy = initDefaultConfig();

        //Init the user file
        resourceUserCopy = initUserConfig();
    }

    /**
     * Loads the root node for the default config File and user config File
     */
    private void loadConfig()
    {
        try {
            final ConfigurationNode defaultConfig = this.defaultCopyLoader.load();
            defaultRootNode = defaultConfig;

            final ConfigurationNode userConfig = this.userCopyLoader.load();
            userRootNode = userConfig;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initializes the YAMLConfigurationLoaders for this config
     */
    private void initConfigLoaders()
    {
        this.defaultCopyLoader = YAMLConfigurationLoader.builder().setPath(resourceConfigCopy.toPath()).setFlowStyle(DumperOptions.FlowStyle.BLOCK).build();
        this.userCopyLoader = YAMLConfigurationLoader.builder().setPath(resourceUserCopy.toPath()).setFlowStyle(DumperOptions.FlowStyle.FLOW).build();
    }

    /**
     * Copies a new file from the JAR to the defaults directory and uses that new file to initialize our resourceConfigCopy
     * @see ConfigurableLoader#resourceConfigCopy
     * @throws IOException
     */
    private File initDefaultConfig() throws IOException {
        return copyDefaultFromJar(getDefaultConfigCopyRelativePath(), true);
    }

    /**
     * Attemps to load the config file if it exists, if it doesn't it copies a new one from within the JAR
     * @return user config File
     * @see ConfigurableLoader#resourceUserCopy
     * @throws IOException
     */
    private File initUserConfig() throws IOException {
        File userCopy = new File(DIRECTORY_DATA_FOLDER, FILE_RELATIVE_PATH); //Load the user file;

        if(userCopy.exists())
        {
            // Yay
            return userCopy;
        }
        else
        {
            //If it's gone we copy default files
            //Note that we don't copy the values from the default copy put in /defaults/ that file exists only as a reference to admins and is unreliable
            return copyDefaultFromJar(FILE_RELATIVE_PATH, false);
        }
    }

    /**
     * Used to make a new config file at a specified relative output path inside the data directory by copying the matching file found in that same relative path within the JAR
     * @param relativeOutputPath the path to the output file
     * @param deleteOld whether or not to delete the existing output file on disk
     * @return a copy of the default config within the JAR
     * @throws IOException
     */
    private File copyDefaultFromJar(String relativeOutputPath, boolean deleteOld) throws IOException
    {
        /*
         * Gen a Default config from inside the JAR
         */
        McmmoCore.getLogger().info("Preparing to copy internal resource file (in JAR) - "+FILE_RELATIVE_PATH);
        InputStream inputStream = McmmoCore.getResource(FILE_RELATIVE_PATH);

        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);

        //This is a copy of the default file, which we will overwrite every time mcMMO loads
        File targetFile = new File(DIRECTORY_DATA_FOLDER, relativeOutputPath);

        //Wipe old default file on disk
        if (targetFile.exists() && deleteOld)
        {
            McmmoCore.getLogger().info("Updating file " + relativeOutputPath);
            targetFile.delete(); //Necessary?
        }

        if(!targetFile.exists())
        {
            targetFile.getParentFile().mkdirs();
            targetFile.createNewFile(); //New File Boys
        }

        Files.write(buffer, targetFile);
        McmmoCore.getLogger().info("Created config file - " + relativeOutputPath);

        inputStream.close(); //Close the input stream

        return targetFile;
    }

    /**
     * The path to the defaults directory
     * @return the path to the defaults directory
     */
    private String getDefaultConfigCopyRelativePath() {
        return DIRECTORY_DEFAULTS + File.separator + FILE_RELATIVE_PATH;
    }

    /**
     * Creates the defaults directory
     */
    private void mkdirDefaults() {
        //Make Default Subdirectory
        File defaultsDir = new File (DIRECTORY_DATA_FOLDER, "defaults");

        if(!defaultsDir.exists())
            defaultsDir.mkdir();
    }

    /**
     * Configs are versioned based on when they had significant changes to keys
     * @return current Config Version String
     */
    public String getVersion()
    {
         return String.valueOf(getConfigVersion());
    }

    /**
     * Attempts to read the loaded config file
     * Config will have any necessary updates applied
     * Config will be compared to the default config to see if it is missing any nodes
     * Config will have any missing nodes inserted with their default value
     */
    public void readConfig() {
        McmmoCore.getLogger().info("Attempting to read " + FILE_RELATIVE_PATH + ".");

        int version = this.userRootNode.getNode("ConfigVersion").getInt();
        McmmoCore.getLogger().info(FILE_RELATIVE_PATH + " version is " + version);

        //Update our config
        updateConfig();
    }

    /**
     * Compares the users config file to the default and adds any missing nodes and applies any necessary updates
     */
    private void updateConfig()
    {
        McmmoCore.getLogger().info(defaultRootNode.getChildrenMap().size() +" items in default children map");
        McmmoCore.getLogger().info(userRootNode.getChildrenMap().size() +" items in default root map");

        // Merge Values from default
        userRootNode = userRootNode.mergeValuesFrom(defaultRootNode);

        // Update config version
        updateConfigVersion();

        //Attempt to save
        try {
            saveUserCopy();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves the current state information of the config to the users copy (which they may edit)
     * @throws IOException
     */
    private void saveUserCopy() throws IOException
    {
        McmmoCore.getLogger().info("Saving new node");
        userCopyLoader.save(userRootNode);
    }

    /**
     * Performs any necessary operations to update this config
     */
    private void updateConfigVersion() {
        // Set a version for our config
        this.userRootNode.getNode("ConfigVersion").setValue(getConfigVersion());
        McmmoCore.getLogger().info("Updated config to ["+getConfigVersion()+"] - " + FILE_RELATIVE_PATH);
    }

    /**
     * Returns the root node of this config
     * @return the root node of this config
     */
    protected ConfigurationNode getUserRootNode() {
        return userRootNode;
    }

    int getIntValue(String... path)
    {
        return userRootNode.getNode(path).getInt();
    }

    double getDoubleValue(String... path)
    {
        return userRootNode.getNode(path).getDouble();
    }

    boolean getBooleanValue(String... path)
    {
        return userRootNode.getNode(path).getBoolean();
    }

    String getStringValue(String... path)
    {
        return userRootNode.getNode(path).getString();
    }
}