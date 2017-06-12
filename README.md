# Sonar History Scanner
Analyses all past revisions of a git project on sonarqube.
## Prerequisites
* Java
* SONAR_SCANNER_HOME environment variable (directed to your sonar-scanner/bin/sonar-scanner location)
## Usage
1. Clone your desired project into a new folder

<code>git clone https://github.com/apache/commons-cli.git /some-folder</code>

2. Create a sonar.properties file within the folder, to specify sonarqube server and other properties

<code>your preferences > /some-folder/sonar.properties</code>

3. Run the jar file (specify the .git location as parameter)

<code>java -jar sonar-history-scanner-0.3.1-jar-with-dependencies.jar --git /some-folder/.git</code>

The jar file will store the sonar-scanner log output in a file called full-log.out
