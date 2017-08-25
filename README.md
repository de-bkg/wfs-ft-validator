# wfs-ft-validator
Command line tool for validating FeatureTypes served by a WFS.

## Installation

### Prerequisites

To build and install the tool, you require
* Java SE Development Kit 8+
* Gradle

### Build

    git clone https://github.com/de-bkg/wfs-ft-validator.git
    cd wfs-ft-validator/
    gradle installDist

## Usage

    cd build/install/wfs-ft-validator
    bin\wfs-ft-validator http://example.com/wfs

### Running behind http proxy

If your network access requires a http proxy you can set the corresponding 
[Network properties](https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html) 
`http.proxyHost`, `http.proxyPort` and `http.nonProxyHosts` for the Java Virtual machine. This is done by
editing the `DEFAULT_JVM_OPTS` variable within the start script for your OS or by dynamically setting the
`JAVA_OPTS` Environment variable.
 
Example Usage Linux:

    export JAVA_OPTS="-Dhttp.proxyHost=your-proxy.net -Dhttp.proxyPort=80 -Dhttp.noProxyHost=*.bkg"

Example Usage Windows:

    SET "JAVA_OPTS=-Dhttp.proxyHost=your-proxy.net -Dhttp.proxyPort=80 -Dhttp.noProxyHost=*.bkg"