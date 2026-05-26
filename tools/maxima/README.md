# Project-local Maxima path

The Docker image installs the Linux Maxima package and creates this project-relative executable path inside the container:

```text
/app/tools/maxima/bin/maxima
```

The REST service receives it through:

```text
GAALOP_MAXIMA_COMMAND=tools/maxima/bin/maxima
```

This keeps the API configuration project-relative while avoiding committing platform-specific Maxima binaries to the repository.
