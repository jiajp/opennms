package org.opennms.core.config.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.opennms.core.config.api.ConfigurationResource;
import org.opennms.core.config.api.ConfigurationResourceException;
import org.opennms.netmgt.config.collectd.CollectdConfiguration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.util.InMemoryResource;

public class JaxbResourceConfigurationTest {
    protected File getConfigFile() throws IOException {
        final File configFile = new File("target/test-classes/collectd-configuration.xml");
        final File tempFile = File.createTempFile("collectd", ".xml", configFile.getParentFile());
        final FileReader reader = new FileReader(configFile);
        final FileWriter writer = new FileWriter(tempFile);
        tempFile.deleteOnExit();
        IOUtils.copy(reader, writer);
        writer.close();
        reader.close();
        return tempFile;
    }

    @Test
    public void testFileSystemResourceExists() throws ConfigurationResourceException, IOException {
        final File configFile = getConfigFile();
        final ConfigurationResource<CollectdConfiguration> collectd = new JaxbResourceConfiguration<CollectdConfiguration>(CollectdConfiguration.class, new FileSystemResource(configFile));
        assertNotNull(collectd);
        final CollectdConfiguration config = collectd.get();
        assertNotNull(config);
        assertEquals(5, config.getPackages().size());
        assertEquals("vmware3", config.getPackages().get(0).getName());
    }

    @Test
    public void testStringResource() throws ConfigurationResourceException, IOException {
        final File configFile = getConfigFile();
        final String configText = IOUtils.toString(configFile.toURI());
        final ConfigurationResource<CollectdConfiguration> collectd = new JaxbResourceConfiguration<CollectdConfiguration>(CollectdConfiguration.class, new InMemoryResource(configText));
        assertNotNull(collectd);
        final CollectdConfiguration config = collectd.get();
        assertNotNull(config);
        assertEquals(5, config.getPackages().size());
        assertEquals("vmware3", config.getPackages().get(0).getName());
    }

    @Test(expected=org.opennms.core.config.api.ConfigurationResourceException.class)
    public void testFileSystemResourceDoesNotExist() throws ConfigurationResourceException {
        final File configFile = new File("target/test-classes/collectd-configuration.x");
        final ConfigurationResource<CollectdConfiguration> collectd = new JaxbResourceConfiguration<CollectdConfiguration>(CollectdConfiguration.class, new FileSystemResource(configFile));
        assertNotNull(collectd);
        collectd.get();
    }

    @Test
    public void testSave() throws ConfigurationResourceException, IOException {
        final File configFile = getConfigFile();
        final ConfigurationResource<CollectdConfiguration> collectd = new JaxbResourceConfiguration<CollectdConfiguration>(CollectdConfiguration.class, new FileSystemResource(configFile));
        assertNotNull(collectd);
        CollectdConfiguration config = collectd.get();
        assertNotNull(config);
        assertEquals(5, config.getPackages().size());
        config.removePackage(config.getPackages().get(0));
        assertEquals("vmware4", config.getPackages().get(0).getName());
        assertEquals(4, config.getPackages().size());
        collectd.save(config);

        config = collectd.get();
        assertNotNull(config);
        assertEquals(4, config.getPackages().size());
        assertEquals("vmware4", config.getPackages().get(0).getName());
    }
}
