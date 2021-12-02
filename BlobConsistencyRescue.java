/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2009, 2010, 2012, 2013, 2014 Zimbra, Inc.
 * Copyright (C) 2019 BTACTIC,SCCL
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */

// Based on BlobConsistencyUtil.java from ZCS 8.6

package com.zimbra.cs.store.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.LinkedHashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.Element.XMLElement;
import com.zimbra.common.util.CliUtil;
import com.zimbra.cs.account.soap.SoapProvisioning;
import com.zimbra.cs.db.DbPool;
import com.zimbra.cs.store.file.BlobConsistencyChecker.BlobInfo;
import com.zimbra.soap.admin.message.ExportAndDeleteItemsRequest;
import com.zimbra.soap.admin.type.ExportAndDeleteMailboxSpec;
import com.zimbra.soap.admin.type.ExportAndDeleteItemSpec;

import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException.NoSuchItemException;
import com.zimbra.cs.mailbox.OperationContext;

public class BlobConsistencyRescue {

    private static final String LO_HELP = "help";
    private static final String LO_VERBOSE = "verbose";
    private static final String LO_MAILBOXES = "mailboxes";
    private static final String LO_VOLUMES = "volumes";
    private static final String LO_SKIP_SIZE_CHECK = "skip-size-check";
    private static final String LO_SUGGESTED_REPAIR_COMMANDS = "suggested-repair-commands-file";
    private static final String LO_EXPORT_DIR = "export-dir";
    private static final String LO_LOSTFOUND_DIRPATH = "lostfound-dir";
    private static final String LO_NO_EXPORT = "no-export";
    private static final String LO_USED_BLOB_LIST = "used-blob-list";

    private Options options;
    private List<Integer> mailboxIds;
    private List<Short> volumeIds = new ArrayList<Short>();
    private boolean skipSizeCheck = false;
    private boolean verbose = false;
    private String suggestedRepairCommands;
    private PrintWriter suggestedRepairWriter;
    private String lostfoundDirpath;
    private boolean outputUsedBlobs = false;
    private String usedBlobList;
    private PrintWriter usedBlobWriter;
    private Map<String, String> lostfoundMap;

    private BlobConsistencyRescue() {
        options = new Options();

        options.addOption(new Option("h", LO_HELP, false, "Display this help message."));
        options.addOption(new Option("v", LO_VERBOSE, false, "Display verbose output.  Display stack trace on error."));
        options.addOption(new Option(null, LO_SKIP_SIZE_CHECK, false, "Skip blob size check."));

        Option o = new Option(null, LO_VOLUMES, true, "Specify which volumes to check.  If not specified, check all volumes.");
        o.setArgName("volume-ids");
        options.addOption(o);

        o = new Option("m", LO_MAILBOXES, true, "Specify which mailboxes to check.  If not specified, check all mailboxes.");
        o.setArgName("mailbox-ids");
        options.addOption(o);

        o = new Option(null, LO_SUGGESTED_REPAIR_COMMANDS, true, "Write the suggested repair commands to a file.");
        o.setArgName("path");
        options.addOption(o);

        o = new Option(null, LO_LOSTFOUND_DIRPATH, true, "Lost+Found or equivalent directory for email files to be checked.");
        o.setArgName("path");
        options.addOption(o);

    }

    private void usage(String errorMsg) {
        int exitStatus = 0;

        if (errorMsg != null) {
            System.err.println(errorMsg);
            exitStatus = 1;
        }
        HelpFormatter format = new HelpFormatter();
        format.printHelp(new PrintWriter(System.err, true), 80,
            "zmblobrescue [options] start", null, options, 2, 2,
            "\nThe \"start\" command is required, to avoid unintentionally running a blob rescue.  " +
            "Id values are separated by commas.");
        System.exit(exitStatus);
    }

    private void parseArgs(String[] args)
    throws ParseException {
        GnuParser parser = new GnuParser();
        CommandLine cl = parser.parse(options, args);

        if (CliUtil.hasOption(cl, LO_HELP)) {
            usage(null);
        }
        // Require the "start" command, so that someone doesn't inadvertently
        // kick of a blob check.
        if (cl.getArgs().length == 0 || !cl.getArgs()[0].equals("start")) {
            usage(null);
        }

        String volumeList = CliUtil.getOptionValue(cl, LO_VOLUMES);
        if (volumeList != null) {
            for (String id : volumeList.split(",")) {
                try {
                    volumeIds.add(Short.parseShort(id));
                } catch (NumberFormatException e) {
                    usage("Invalid volume id: " + id);
                }
            }
        }

        String mailboxList = CliUtil.getOptionValue(cl, LO_MAILBOXES);
        if (mailboxList != null) {
            mailboxIds = new ArrayList<Integer>();
            for (String id : mailboxList.split(",")) {
                try {
                    mailboxIds.add(Integer.parseInt(id));
                } catch (NumberFormatException e) {
                    usage("Invalid mailbox id: " + id);
                }
            }
        }

        skipSizeCheck = CliUtil.hasOption(cl, LO_SKIP_SIZE_CHECK);
        verbose = CliUtil.hasOption(cl, LO_VERBOSE);

        suggestedRepairCommands = CliUtil.getOptionValue(cl, LO_SUGGESTED_REPAIR_COMMANDS);
        lostfoundDirpath = CliUtil.getOptionValue(cl, LO_LOSTFOUND_DIRPATH);

        if (lostfoundDirpath == null) {
                usage("Missing: " + LO_LOSTFOUND_DIRPATH + "!");
        }

    }

    private void readLostDir()
    throws IOException {
        InputStream inputStream;
        lostfoundMap = new LinkedHashMap<>();
        System.out.format("Scanning: '%s' lost+found directory.\n", lostfoundDirpath);
        File lostfoundDir = new File(lostfoundDirpath);
        File[] files = lostfoundDir.listFiles();
        if (files == null) {
            files = new File[0];
        }

        for (File file : files) {
            inputStream = new FileInputStream(file);
            String shadigest_string = com.zimbra.common.util.ByteUtil.getSHA256Digest(inputStream,true);
            lostfoundMap.put(shadigest_string, file.getAbsolutePath());
        }
    }
    private void run()
    throws Exception {

        if (suggestedRepairCommands != null) {
            suggestedRepairWriter = new PrintWriter(new FileOutputStream(suggestedRepairCommands), true);
        }

        CliUtil.toolSetup();
        SoapProvisioning prov = SoapProvisioning.getAdminInstance();
        prov.soapZimbraAdminAuthenticate();
        if (mailboxIds == null) {
            mailboxIds = getAllMailboxIds(prov);
        }
        try {
            DbPool.startup();
            readLostDir();
            for (int mboxId : mailboxIds) {
                System.out.println("Checking mailbox " + mboxId + ".");
                checkMailbox(mboxId, prov);
            }
        }  finally{
            DbPool.shutdown();
        }
        if (suggestedRepairWriter != null) {
            suggestedRepairWriter.close();
        }

        if (usedBlobWriter != null) {
            usedBlobWriter.close();
        }
    }

    private List<Integer> getAllMailboxIds(SoapProvisioning prov)
    throws ServiceException {
        List<Integer> ids = new ArrayList<Integer>();
        XMLElement request = new XMLElement(AdminConstants.GET_ALL_MAILBOXES_REQUEST);
        Element response = prov.invoke(request);
        for (Element mboxEl : response.listElements(AdminConstants.E_MAILBOX)) {
            ids.add((int) mboxEl.getAttributeLong(AdminConstants.A_ID));
        }
        return ids;
    }

    private String locatorText(BlobInfo blob) {
        if (blob.external) {
            return String.format("locator %s", blob.path);
        } else {
            return String.format("volume %d, %s", blob.volumeId, blob.path);
        }
    }

    private void checkMailbox(int mboxId, SoapProvisioning prov)
    throws ServiceException {
        BlobInfo blob = new BlobInfo();
        Mailbox mbox;
        MailItem mailItem;
        String mailItemDigest;
        OperationContext mboxOctxt;
        XMLElement request = new XMLElement(AdminConstants.CHECK_BLOB_CONSISTENCY_REQUEST);
        for (short volumeId : volumeIds) {
            request.addElement(AdminConstants.E_VOLUME).addAttribute(AdminConstants.A_ID, volumeId);
        }
        request.addElement(AdminConstants.E_MAILBOX).addAttribute(AdminConstants.A_ID, mboxId);
        request.addAttribute(AdminConstants.A_CHECK_SIZE, !skipSizeCheck);
        request.addAttribute(AdminConstants.A_REPORT_USED_BLOBS, false);

        Element response = prov.invoke(request);
        for (Element mboxEl : response.listElements(AdminConstants.E_MAILBOX)) {
            mbox = MailboxManager.getInstance().getMailboxById((int) mboxEl.getAttributeLong(AdminConstants.A_ID));
            // Print results.
            BlobConsistencyChecker.Results results = new BlobConsistencyChecker.Results(mboxEl);
            for (Element item : mboxEl.getElement(AdminConstants.E_MISSING_BLOBS).listElements(AdminConstants.E_ITEM)) {
                blob = new BlobInfo();
                blob.itemId = (int) item.getAttributeLong(AdminConstants.A_ID);
                blob.modContent = (int) item.getAttributeLong(AdminConstants.A_REVISION);
                blob.dbSize = item.getAttributeLong(AdminConstants.A_SIZE);
                blob.volumeId = (short) item.getAttributeLong(AdminConstants.A_VOLUME_ID);
                blob.path = item.getAttribute(AdminConstants.A_BLOB_PATH);
                blob.external = item.getAttributeBool(AdminConstants.A_EXTERNAL, false);
                blob.version = (int) item.getAttributeLong(AdminConstants.A_VERSION_INFO_VERSION);


                mboxOctxt = new OperationContext(mbox);
                String foundFilePath = null;
                try {
                    mailItem = mbox.getItemById(mboxOctxt,blob.itemId,MailItem.Type.UNKNOWN);
                    mailItemDigest = mailItem.getDigest();
                    foundFilePath = lostfoundMap.get(mailItemDigest);
                }
                catch (NoSuchItemException e) {
                    foundFilePath = null;
                }


                System.out.format("Mailbox %d, item %d, rev %d, %s: blob not found.\n",
                    results.mboxId, blob.itemId, blob.modContent, locatorText(blob));
                String suggestedRepairCommandLine;
                if (foundFilePath == null) {
                    suggestedRepairCommandLine = String.format("echo 'EMAIL_EMPTY_CONTENT' > '%s'", blob.path
);
                } else {
                    suggestedRepairCommandLine = String.format("cp '%s' '%s'", foundFilePath, blob.path);
                }

                System.out.println("#"+suggestedRepairCommandLine); // Make the default output to be a comment
                if (suggestedRepairWriter != null) {
                    suggestedRepairWriter.println(suggestedRepairCommandLine);
                }

            }
        }
    }

    public static void main(String[] args) {
        BlobConsistencyRescue app = new BlobConsistencyRescue();

        try {
            app.parseArgs(args);
        } catch (ParseException e) {
            app.usage(e.getMessage());
        }

        try {
            app.run();
        } catch (Exception e) {
            if (app.verbose) {
                e.printStackTrace(new PrintWriter(System.err, true));
            } else {
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.toString();
                }
                System.err.println(msg);
            }
            System.exit(1);
        }
    }

}
