zmblobrescue
==========

Write suggested commands to rescue mail items or blobs from a lost+found directory.

Designed for Zimbra version 8.8.X.

Bugs and feedback: [https://github.com/btactic/zmblobrescue/issues](https://github.com/btactic/zmblobrescue/issues)

### Purpose
There is not a safe or perfect Zimbra system. If your mailbox storage is the default one which uses an underlying filesystem you might some day face a filesystem corruption problem.

Suddenly many of the emails content appear with the *No such blob* message.
Is it everything lost?
No, running *fsck -fy* on the (previously cloned) mailbox filesystem might generate a lost+found directory with many of these lost emails and blobs.

Using zmblobchk let's you identify many of the mailboxes with *No such blob* messages. Taking a look at the [mailbox associated mysql](https://wiki.zimbra.com/wiki/Account_mailbox_database_structure) database manually might help you on identifying which it's the right blob. For one or two important emails.

Too many mailboxes that have to be matched with too many *recovered* mail items or blobs.


Now you can use zmblobrescue to solve your problem.

zmblobrescue will gather the mysql blob associated digest, generate the lost-and-found blobs digests and compare both of them. If there is a match you can recover that email. The final result is a set of commands that you can use to recover your lost and found files in a breeze.

Note that emails or blobs that do not have a matching lost-and-found file will be saved with *EMAIL_EMPTY_CONTENT* string in it. That avoids problems with imap clients being unable to download the mailbox contents.


### Installation
```
# Login as zimbra
sudo su - zimbra
mkdir -p /opt/zimbra/conf/scripts
cd /opt/zimbra/conf/scripts
wget "https://github.com/btactic/zmblobrescue/archive/v1.1.tar.gz" -O /tmp/zmblobrescue-v1.1.tar.gz
tar xzf /tmp/zmblobrescue-v1.1.tar.gz
cd zmblobrescue-1.1
./build.sh
```

### Usage
```
usage: zmblobrescue [options] start
  -h,--help                                   Display this help message.
     --lostfound-dir <path>                   Lost+Found or equivalent directory
                                              for email files to be checked.
  -m,--mailboxes <mailbox-ids>                Specify which mailboxes to check.
                                              If not specified, check all
                                              mailboxes.
     --skip-size-check                        Skip blob size check.
     --suggested-repair-commands-file <path>  Write the suggested repair
                                              commands to a file.
  -v,--verbose                                Display verbose output.  Display
                                              stack trace on error.
     --volumes <volume-ids>                   Specify which volumes to check.
                                              If not specified, check all
                                              volumes.

The "start" command is required, to avoid unintentionally running a blob check.
Id values are separated by commas.
```

### CLI usage example
```
# Login as zimbra
sudo su - zimbra
cd /opt/zimbra/conf/scripts/zmblobrescue-1.1
./zmblobrescue.sh \
    --mailboxes 200 \
    --volumes 3 \
    --lostfound-dir /opt/zimbra/conf/scripts/zimbra_lostfound \
    --suggested-repair-commands-file suggested_commands.txt \
    -v start
```

where 200 is the mailbox id to be rescued and 3 is the volume id to be rescued.

### Useful usage
After running zmblobrescue you might want to run under your risk the commands found on the suggested_commands.txt generated file.

### Historical notes
This program is originally based on the BlobConsistencyUtil.java source code from ZCS 8.6.

### TODO
 - Build a jar and install it into normal jar folder
 - Write a wrapper around zmjava like actual zmblobchk does
 - Implement rescue (copy and echo of files) into the java code and not using an external bash file
 - Add an optional option to perform the actual rescue
 - Make sure external blobs are ignored
 - Change build system to an ant based one
 - Use something more than digest value as from or to values to avoid hash collisions
 - Better memory handling when reading all the files from the lost+found directory


### License
zmblobrescue

Copyright (C) 2009, 2010, 2012, 2013, 2014 Zimbra, Inc.

Copyright (C) 2019, 2020, 2021, 2022 [BTACTIC](http://www.btactic.com/),SCCL

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software Foundation,
version 2 of the License.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with this program.
If not, see <http://www.gnu.org/licenses/>.
