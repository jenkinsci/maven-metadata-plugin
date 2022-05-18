## Version History

### 2.0.0 (10. January 2018)

- Return param value to Pipeline.
- Update to Jenkins 2 to address SECURITY-170.

### 1.5.0 (31. May 2016)

- Add support for rebuilding.

### 1.4.1 (14. March 2016)

- Fix severe bug that was introduced with version 1.4.0 - sorry for that.

### 1.4.0 (04. March 2016)

- Add classifier support.

### 1.3.0 (10. November 2015)

- Add optional configurations that allows to download and display information about currently used artifact.
- Use Jenkins credentials to store repository username and password. With this, passwords are encrypted and no longer stored as plain text in the job's `config.xml` file.
- Bugfix: Invalid artifact URL when packaging is not specified.

### 1.2.0 (26. November 2014)

- Add support for SNAPSHOTs. The plugin should now properly resolve SNAPSHOT versions to their timestamped URLs. [JENKINS-23206](https://issues.jenkins-ci.org/browse/JENKINS-23206)
- Thanks to [Mathieu Pousse](https://github.com/mathieu-pousse) for providing the pull request for this change.

### 1.1.1 (14. February 2014)

- Add support for the command line interface. If you have a property named `MY_JAR` you should now be able to append -p `MY_JAR=1.3.42` and the job will be started for version "1.3.42" without scanning the Maven repository. All of the above mentioned parameters should still be set for your build steps using the version you provided on the command line.
- Thanks to [David Portabella](https://github.com/dportabella) for providing the pull request for this change.

### 1.1.0 (13. February 2014)

- Correct the help message. (Was copy-pasted from another plugin).
- Add a username and password fields which will override the previous way of specifying username:password as part of URL - no need to add them in url-encoded, plain-text to the URL.
- Thanks to [Robert Kleinschmager](https://github.com/barclay-reg) and [Dominik Bartholdi](https://github.com/imod) for providing the pull requests for these changes.

### 1.0.0 (16. April 2012)

- Initial version.
