# Sonar History Scanner
Runs sonarqube analysis on past revisions of a git project.
## Prerequisites
* Java
* SONAR_SCANNER_HOME environment variable (directed to your sonar-scanner/bin/sonar-scanner location).
You must be able to run "sonnar-scanner" or "sonnar-scanner.bat" in any directory.
## Usage

#### Simple

1. Create a "sonar-history.properties" file in the folder where you run the .jar, to specify sonarqube project properties.

2. Run analysis for each past revision on a public git project:

`java -jar sonar-history-scanner.jar --git "https://github.com/cuuzis/java-project-for-sonar-scanner-testing.git"`

#### Advanced
| Argument  | Value |
| ------------- | ------------- |
| -g, --git (_mandatory_) | Path to project's git repository. Can be local (e.g. "C:\...\project1\.git") or remote (e.g. "https:/github.com/.../project1/.git")  |
| -s, --since <arg>  | Run analysis only on revisions since commit (included) with this SHA |
| -u, --until  | Run analysis only on revisions until commit (included) with this SHA |
| -d, --directory  | Directory in which sonarqube analysis should be performed (default is current directory) |
| -p, --properties  | Sonar scanner properties filename (default is "sonar-history.properties") |
| -c, --change  | Change property files at these revisions. _arg0_ is sonar-x.properties files, separated by commas. _arg1_ is revisions to use these property files since, separated by commas. (e.g. <code>--change properties1,properties2 hash1,hash2)</code> |
| -e, --every  | Scan only every _arg_ revision. |
| -f, --file  | scan only revisions specified in file by this filename. Each row in the file must be a revision hash. |

Arguments can be combined and used in any order, like in more complex cases:
```
--git "https://github.com/cuuzis/java-project-for-sonar-scanner-testing.git" --since 1129ecb26f75a3e43cf6305186f7723e99036ec9 --properties my.properties --change my2.properties,my3.properties 1129ecb26f75a3e43cf6305186f7723e99036ec9,6a5d6ed39dadcef676d87fa529014093b44e794b --until c3bd38b8aaaf0e816fdd6e8f1fd4e81201121f3e --directory /var/squasme/
```