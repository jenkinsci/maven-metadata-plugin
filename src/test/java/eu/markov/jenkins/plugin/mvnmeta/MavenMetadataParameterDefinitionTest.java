package eu.markov.jenkins.plugin.mvnmeta;

import hudson.util.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.Assert;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MavenMetadataParameterDefinitionTest {

    private static final String CURRENT_ARTIFACT_INFO_URL = MavenMetadataParameterDefinitionTest.class.getResource("currentArtifactInfo.txt").toExternalForm();

    private ServerSocket server = null;

    @Before
    public void startWebServer() {
        try {
            this.server = new ServerSocket(0);
            System.out.println("Server is now listening on " + this.server.getLocalPort());
        } catch (final IOException e) {
            throw new IllegalStateException("cannot start server");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //right now the stream is open.
                    final Socket clientSocket = MavenMetadataParameterDefinitionTest.this.server.accept();
                    OutputStreamWriter outWriter = null;
                    try {
                        BufferedReader din = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "ASCII"));
                        outWriter = new OutputStreamWriter(clientSocket.getOutputStream());
                        String line = null;
                        String request = null;
                        while ((line = din.readLine()) != null) {
                            if (request == null) {
                                request = line;
                            }
                            System.out.println("< " + line);
                            if (line.length() == 0) {
                                break;
                            }
                        }

                        if (request == null || !request.startsWith("GET ") || !request.endsWith(" HTTP/1.1")) {
                            throw new IllegalArgumentException("invalid request");
                        }

                        String resource = request.substring(4, request.length() - 9);
                        URL resourceURL = MavenMetadataParameterDefinitionTest.class.getResource(resource);
                        if (resourceURL == null) {
                            System.out.println("Resource [" + resource + "] not found");
                            outWriter.write("HTTP/1.1 404 Not found\r\n");
                        } else {
                            System.out.println("Serving resource [" + resource + "]");
                            //here write the content type etc details:
                            outWriter.write("HTTP/1.1 200 OK\r\n");
                            outWriter.write("Content-type: application/xml\r\n");
                            outWriter.write("Server: junit\r\n");
                            outWriter.write("\r\n");
                            IOUtils.copy(MavenMetadataParameterDefinitionTest.class.getResourceAsStream(resource), outWriter);
                        }
                    } catch (final EOFException e) {
                        System.out.println("EOF: " + e.getMessage());
                    } catch (final IOException e) {
                        System.out.println("IO at run: " + e.getMessage());
                    } finally {
                        try {
                            if (outWriter != null) {
                                outWriter.close();
                            }
                            clientSocket.close();
                        } catch (final IOException e) {
                            System.out.println("Unable to close the socket");
                        }
                    }
                    //now the connection is established
                } catch (final IOException e) {
                    System.out.println("Unable to read: " + e.getMessage());
                } finally {
                    try {
                        if (MavenMetadataParameterDefinitionTest.this.server != null) {
                            MavenMetadataParameterDefinitionTest.this.server.close();
                        }
                    } catch (final IOException e) {
                        // ignored
                    }
                }
            }
        }).start();
    }

    @After
    public void stopWebServer() {
        try {
            if (this.server != null) {
                this.server.close();
            }
        } catch (final IOException e) {
            // ignored
        }
    }

    public String getLocalWebServerUrl() {
        return "http://localhost:" + this.server.getLocalPort();
    }


    @Test
    public void testSingleSnapshot() {
        MavenMetadataParameterDefinition definition = new MavenMetadataParameterDefinition("variable", "void", getLocalWebServerUrl(), "com.acme", "single", "jar","", "", "DESC", "null", "10", "", "", "", null);
        MavenMetadataParameterValue result = (MavenMetadataParameterValue) definition.createValue(null, "3.8-SNAPSHOT");
        assertTrue(result.getArtifactUrl(), result.getArtifactUrl().endsWith("3.8-SNAPSHOT.jar"));
    }

    @Test
    public void testTimestampedSnapshot() {
        MavenMetadataParameterDefinition definition = new MavenMetadataParameterDefinition("variable", "void", getLocalWebServerUrl(), "com.acme", "timestamped", "jar","", "", "DESC", "null", "10", "", "", "", null);
        MavenMetadataParameterValue result = (MavenMetadataParameterValue) definition.createValue(null, "3.8-SNAPSHOT");
        assertTrue(result.getArtifactUrl(), result.getArtifactUrl().endsWith("3.8-20140919.030038-76.jar"));
    }

    @Test
    public void testClassifierSnapshot() {
        MavenMetadataParameterDefinition definition = new MavenMetadataParameterDefinition("variable", "void", getLocalWebServerUrl(), "com.acme", "classifier", "jar","classes", "", "DESC", "null", "10", "", "", "", null);
        MavenMetadataParameterValue result = (MavenMetadataParameterValue) definition.createValue(null, "3.8-SNAPSHOT");
        assertTrue(result.getArtifactUrl(), result.getArtifactUrl().endsWith("3.8-20140919.030038-76-classes.jar"));
    }
    
    @Test
    public void testListVersionsSingleSnapshot() {
        MavenMetadataParameterDefinition definition = new MavenMetadataParameterDefinition("variable", "void", getLocalWebServerUrl(), "com.acme", "timestamped", "jar","", "", "ASC", "null", "10", "", "", "", null);
        List<String> versions = definition.getVersions();
        Assert.notEmpty(versions);
        assertEquals("3.6", versions.get(0));
    }

    @Test
    public void testListVersionsTimestampedSnapshot() {
        MavenMetadataParameterDefinition definition = new MavenMetadataParameterDefinition("variable", "void", getLocalWebServerUrl(), "com.acme", "timestamped", "jar","", "", "DESC", "null", "2", "", "", "", null);
        List<String> versions = definition.getVersions();
        Assert.notEmpty(versions);
        assertEquals(2, versions.size());
        assertEquals("3.8-SNAPSHOT", versions.get(0));
    }

    
    @Test
    public void testListVersionsClassifierSnapshot() {
        MavenMetadataParameterDefinition definition = new MavenMetadataParameterDefinition("variable", "void", getLocalWebServerUrl(), "com.acme", "classifier", "jar","classes", "", "DESC", "null", "2", "", "", "", null);
        List<String> versions = definition.getVersions();
        Assert.notEmpty(versions);
        assertEquals(2, versions.size());
        assertEquals("3.8-SNAPSHOT", versions.get(0));
    }

    
    @Test
    public void testGetCurrentArtifactInfoPatternWithCapturingGroup() {
        this.executeTestGetCurrentArtifactInfoPattern(CURRENT_ARTIFACT_INFO_URL, "My Label", "Plugin Version: ([\\S]+)", "My Label: 3.14159");
    }

    @Test
    public void testGetCurrentArtifactInfoPatternWithoutCapturingGroup() {
        this.executeTestGetCurrentArtifactInfoPattern(CURRENT_ARTIFACT_INFO_URL, "My Label", "Plugin Version: [\\S]+", "My Label: Plugin Version: 3.14159");
    }

    @Test
    public void testGetCurrentArtifactInfoWithNonmatchingPattern() {
        this.executeTestGetCurrentArtifactInfoPattern(CURRENT_ARTIFACT_INFO_URL, "My Label", "Not Matching: [\\S]+", "My Label: Artifact ID: my-artifact\nPlugin Version: 3.14159\nJenkins Version:  42\n");
    }

    @Test
    public void testGetCurrentArtifactInfoWithoutPattern() {
        this.executeTestGetCurrentArtifactInfoPattern(CURRENT_ARTIFACT_INFO_URL, "My Label", null, "My Label: Artifact ID: my-artifact\nPlugin Version: 3.14159\nJenkins Version:  42\n");
    }

    @Test
    public void testGetCurrentArtifactInfoPatternWithDefaultLabel() {
        this.executeTestGetCurrentArtifactInfoPattern(CURRENT_ARTIFACT_INFO_URL, null, "Plugin Version: ([\\S]+)", "Currently used artifact: 3.14159");
    }

    @Test
    public void testGetCurrentArtifactInfoPatternWithoutUrl() {
        this.executeTestGetCurrentArtifactInfoPattern(null, "My Label", "Plugin Version: ([\\S]+)", "");
    }

    private void executeTestGetCurrentArtifactInfoPattern(String url, String label, String pattern, String expectedResult) {
        MavenMetadataParameterDefinition definition =
            new MavenMetadataParameterDefinition("", "", getLocalWebServerUrl(), "", "", "","", "", "DESC", "", "", url, label, pattern, null);
        assertEquals(expectedResult, definition.getCurrentArtifactInfo());
    }
}
