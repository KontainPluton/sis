### List of all directory moves for modularization :

#### SIS CORE
```shell
# SIS CORE
mv core/sis-utility/src/main src/main/java/org.apache.sis.util/src/main
mv src/main/java/org.apache.sis.util/src/main/java src/main/java/org.apache.sis.util
mv core/sis-utility/src/test/java src/test/java/org.apache.sis.util

# SIS METADATA
mv core/sis-metadata/src/main src/main/java/org.apache.sis.metadata/src/main
mv src/main/java/org.apache.sis.metadata/src/main/java src/main/java/org.apache.sis.metadata
mv core/sis-metadata/src/test src/test/java/org.apache.sis.metadata/src/test
mv src/test/java/org.apache.sis.metadata/src/test/java src/test/java/org.apache.sis.metadata

# SIS REFERENCING
mv core/sis-referencing/src/main src/main/java/org.apache.sis.referencing/src/main
mv src/main/java/org.apache.sis.referencing/src/main/java src/main/java/org.apache.sis.referencing
mv core/sis-referencing/src/test src/test/java/org.apache.sis.referencing/src/test
mv src/test/java/org.apache.sis.referencing/src/test/java src/test/java/org.apache.sis.referencing

# SIS FEATURE
mv core/sis-feature/src/main/java src/main/java/org.apache.sis.feature
mv core/sis-feature/src/test/java src/test/java/org.apache.sis.feature

# SIS REFERENCING GAZETTEER
mv core/sis-referencing-by-indentifiers/src/main/java src/main/java/org.apache.sis.referencing.gazetteer
mv core/sis-referencing-by-indentifiers/src/test/java src/test/java/org.apache.sis.referencing.gazetteer

# SIS PORTRAYAL
mv core/sis-portrayal/src/main/java src/main/java/org.apache.sis.portrayal
mv core/sis-portrayal/src/test/java src/test/java/org.apache.sis.portrayal
```

#### SIS STORAGE
```shell
# SIS STORAGE
mv storage/sis-storage/src/main src/main/java/org.apache.sis.storage/src/main
mv src/main/java/org.apache.sis.storage/src/main/java src/main/java/org.apache.sis.storage
mv storage/sis-storage/src/test src/test/java/org.apache.sis.storage/src/test
mv src/test/java/org.apache.sis.storage/src/test/java src/test/java/org.apache.sis.storage

# SIS XMLSTORE
mv storage/sis-xmlstore/src/main src/main/java/org.apache.sis.storage.xml/src/main
mv src/main/java/org.apache.sis.storage.xml/src/main/java src/main/java/org.apache.sis.storage.xml
mv storage/sis-xmlstore/src/test src/test/java/org.apache.sis.storage.xml/src/test
mv src/test/java/org.apache.sis.storage.xml/src/test/java src/test/java/org.apache.sis.storage.xml

# SIS SQLSTORE
mv storage/sis-sqlstore/src/main/java src/main/java/org.apache.sis.storage.sql
mv storage/sis-sqlstore/src/test src/test/java/org.apache.sis.storage.sql/src/test
mv src/test/java/org.apache.sis.storage.sql/src/test/java src/test/java/org.apache.sis.storage.sql

# SIS SHAPEFILE
mv storage/sis-shapefile/src/main src/main/java/org.apache.sis.storage.shapefile/src/main
mv src/main/java/org.apache.sis.storage.shapefile/src/main/java src/main/java/org.apache.sis.storage.shapefile
mv storage/sis-shapefile/src/test src/test/java/org.apache.sis.storage.shapefile/src/test
mv src/test/java/org.apache.sis.storage.shapefile/src/test/java src/test/java/org.apache.sis.storage.shapefile

# SIS NETCDF
mv storage/sis-netcdf/src/main src/main/java/org.apache.sis.storage.netcdf/src/main
mv src/main/java/org.apache.sis.storage.netcdf/src/main/java src/main/java/org.apache.sis.storage.netcdf
mv storage/sis-netcdf/src/test/java src/test/java/org.apache.sis.storage.netcdf

# SIS GEOTIFF
mv storage/sis-geotiff/src/main src/main/java/org.apache.sis.storage.geotiff/src/main
mv src/main/java/org.apache.sis.storage.geotiff/src/main/java src/main/java/org.apache.sis.storage.geotiff
mv storage/sis-geotiff/src/test/java src/test/java/org.apache.sis.storage.netcdf

# SIS EARTH OBSERVATION
mv storage/sis-earth-observation/src/main src/main/java/org.apache.sis.storage.earthobservation/src/main
mv src/main/java/org.apache.sis.storage.earthobservation/src/main/java src/main/java/org.apache.sis.storage.earthobservation
mv storage/sis-earth-observation/src/test src/test/java/org.apache.sis.storage.earthobservation/src/test
mv src/test/java/org.apache.sis.storage.earthobservation/src/test/java src/test/java/org.apache.sis.storage.earthobservation
```

#### SIS PROFILE
```shell
# SIS FRENCH PROFILE
mv profiles/sis-french-profile/src/main src/main/java/org.apache.sis.profile.france/src/main
mv src/main/java/org.apache.sis.profile.france/src/main/java src/main/java/org.apache.sis.profile.france
mv profiles/sis-french-profile/src/test src/test/java/org.apache.sis.profile.france/src/test
mv src/test/java/org.apache.sis.profile.france/src/test/java src/test/java/org.apache.sis.profile.france

# SIS JAPAN PROFILE
mv profiles/sis-japan-profile/src/main src/main/java/org.apache.sis.profile.japan/src/main
mv src/main/java/org.apache.sis.profile.japan/src/main/java src/main/java/org.apache.sis.profile.japan
mv profiles/sis-japan-profile/src/test/java src/test/java/org.apache.sis.profile.japan
```

#### SIS CLOUD
```shell
# SIS CLOUD AWS
mv cloud/sis-cloud-aws/src/main src/main/java/org.apache.sis.cloud.aws/src/main
mv src/main/java/org.apache.sis.cloud.aws/src/main/java src/main/java/org.apache.sis.cloud.aws
mv cloud/sis-cloud-aws/src/test/java src/test/java/org.apache.sis.cloud.aws
```

#### SIS APPLICATION
```shell
# SIS CONSOLE
mv application/sis-console/src/main src/main/java/org.apache.sis.console/src/main
mv src/main/java/org.apache.sis.console/src/main/java src/main/java/org.apache.sis.console
mv application/sis-console/src/test/java src/test/java/org.apache.sis.console

# SIS OPENOFFICE
mv application/sis-openoffice/src/main src/main/java/org.apache.sis.openoffice/src/main
mv src/main/java/org.apache.sis.openoffice/src/main/java src/main/java/org.apache.sis.openoffice
mv application/sis-openoffice/src/test/java src/test/java/org.apache.sis.openoffice

# SIS WEBAPP
mv application/sis-webapp/src/main src/main/java/org.apache.sis.webapp/src/main
mv src/main/java/org.apache.sis.webapp/src/main/java src/main/java/org.apache.sis.webapp
mkdir src/test/java/org.apache.sis.webapp
```