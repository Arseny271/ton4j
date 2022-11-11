package org.ton.java.smartcontract.integrationtests;

import com.iwebpp.crypto.TweetNaclFast;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ton.java.address.Address;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.smartcontract.TestFaucet;
import org.ton.java.smartcontract.dns.Dns;
import org.ton.java.smartcontract.dns.DnsCollection;
import org.ton.java.smartcontract.dns.DnsItem;
import org.ton.java.smartcontract.dns.DnsUtils;
import org.ton.java.smartcontract.nft.NftUtils;
import org.ton.java.smartcontract.types.*;
import org.ton.java.smartcontract.wallet.Options;
import org.ton.java.smartcontract.wallet.Wallet;
import org.ton.java.smartcontract.wallet.v3.WalletV3ContractR1;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.tonlib.types.RawAccountState;
import org.ton.java.utils.Utils;

import java.math.BigInteger;

import static org.ton.java.smartcontract.dns.Dns.DNS_CATEGORY_NEXT_RESOLVER;
import static org.ton.java.smartcontract.dns.Dns.DNS_CATEGORY_WALLET;

@Slf4j
@RunWith(JUnit4.class)
public class TestDns {
    static TweetNaclFast.Signature.KeyPair adminKeyPair1;
    static WalletV3ContractR1 adminWallet;
    static Tonlib tonlib = Tonlib.builder()
            .testnet(true)
//            .verbosityLevel(VerbosityLevel.DEBUG)
            .build();

    @BeforeClass
    public static void setUpClass() throws InterruptedException {
        String predefinedSecretKey = "9a98f996e91dea81560cd539f725ef01456705220ca2eb314ac547ed21bbc161235dc8daef9f3e9282963356a668b4b71329ad4743dc709674aec4a826fc750b";
//        String predefinedSecretKey = "";
//        test-wallet init address 0QDbgKZ6Xd3u-q6PuDHbZTwFiBv1N2-FHIJuQ8xzd27X6tw-
//        raw address 0:db80a67a5dddeefaae8fb831db653c05881bf5376f851c826e43cc73776ed7ea

        if (StringUtils.isEmpty(predefinedSecretKey)) {
            adminKeyPair1 = Utils.generateSignatureKeyPair();
        } else {
            adminKeyPair1 = Utils.generateSignatureKeyPairFromSeed(Utils.hexToBytes(predefinedSecretKey));
        }

        log.info("pubKey {}, prvKey {}", Utils.bytesToHex(adminKeyPair1.getPublicKey()), Utils.bytesToHex(adminKeyPair1.getSecretKey()));

        Options options = Options.builder()
                .publicKey(adminKeyPair1.getPublicKey())
                .wc(0)
                .build();

        Wallet walletcontract = new Wallet(WalletVersion.v3R1, options);
        adminWallet = walletcontract.create();

        InitExternalMessage msg = adminWallet.createInitExternalMessage(adminKeyPair1.getSecretKey());
        Address address = msg.address;

        String nonBounceableAddress = address.toString(true, true, false, true);
        String bounceableAddress = address.toString(true, true, true, true);

        log.info("\nNon-bounceable address (for init): {}\nBounceable address (for later access): {}\nraw: {}", nonBounceableAddress, bounceableAddress, address.toString(false));

        if (StringUtils.isEmpty(predefinedSecretKey)) {
            BigInteger balance = TestFaucet.topUpContract(tonlib, Address.of(nonBounceableAddress), Utils.toNano(50));
            log.info("new wallet balance {}", Utils.formatNanoValue(balance));
            // deploy new wallet
            tonlib.sendRawMessage(Utils.bytesToBase64(msg.message.toBoc(false)));
        }

        long seqno = adminWallet.getSeqno(tonlib);
        log.info("wallet seqno {}", seqno);
    }

    @Test
    public void testDnsResolve() {

        Dns dns = new Dns(tonlib);
        Address rootAddress = dns.getRootDnsAddress();
        log.info("root DNS address = {}", rootAddress.toString(true, true, true));

        Object result = dns.resolve("apple.ton", DNS_CATEGORY_NEXT_RESOLVER, true);
        log.info("apple.ton resolved to {}", ((Address) result).toString(true, true, true));

//        Address addr = (Address) dns.getWalletAddress("foundation.ton"); // not in current test net?
//        log.info(addr.toString(true, true, true));
    }

    @Test
    public void testDnsRootDeploy() {
        DnsRoot dnsRoot = new DnsRoot();
        log.info("new root DNS address {}", dnsRoot.getAddress().toString(true, true, true));

        dnsRoot.deploy(tonlib, adminWallet, adminKeyPair1);

        Utils.sleep(20);

        RawAccountState state;
        do {
            Utils.sleep(5);
            state = tonlib.getRawAccountState(dnsRoot.getAddress());
        } while (StringUtils.isEmpty(state.getCode()));

        log.info("root dns account state {}", state);
    }

    @Test
    public void testDnsCollectionDeploy() {
        String dnsItemCodeHex = "B5EE9C7241022801000698000114FF00F4A413F4BCF2C80B0102016202030202CC04050201201E1F02012006070201481819020120080902015816170201200A0B000D470C8CB01C9D0801F73E09DBC400B434C0C05C6C2497C1383E903E900C7E800C5C75C87E800C7E800C3C0289ECE39397C15B088D148CB1C17CB865407E90350C1B5C3232C1FD00327E08E08418B9101A68608209E3402A4108308324CC200337A0404B20403C162A20032A41287E08C0683C00911DFC02440D7E08FC02F814D671C1462C200C00113E910C1C2EBCB8536003F88E34109B5F0BFA40307020F8256D8040708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB00E029C70091709509D31F50AAE221F008F82321BC24C0008E9E343A3A3B8E1636363737375135C705F2E196102510241023F823F00BE30EE0310DD33F256EB31FB0926C21E30D0D0E0F00FE302680698064A98452B0BEF2E19782103B9ACA0052A0A15270BC993682103B9ACA0019A193390805E220C2008E328210557CEA20F82510396D71708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB00923036E2803C23F823A1A120C2009313A0029130E24474F0091024F823F00B00D2343653CDA182103B9ACA005210A15270BC993682103B9ACA0016A1923005E220C2008E378210370FEC516D72295134544743708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB001CA10B9130E26D5477655477632EF00B0204C882105FCC3D145220BA8E9531373B5372C705F2E191109A104910384706401504E082101A0B9D515220BA8E195B32353537375135C705F2E19A03D4304015045033F823F00BE02182104EB1F0F9BAE3023B20821044BEAE41BAE302382782104ED14B65BA1310111200885B363638385147C705F2E19B04D3FF20D74AC20007D0D30701C000F2E19CF404300798D43040168307F417983050058307F45B30E270C8CB07F400C910354014F823F00B01FE30363A246EF2E19D8050F833D0F4043052408307F40E6FA1F2E19FD30721C00022C001B1F2E1A021C0008E9124109B1068517A10571046105C43144CDD9630103A395F07E201C0018E32708210370FEC51586D8100A0708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB00915BE21301FE8E7A37F8235006A1810258BC066E16B0F2E19E23D0D749F823F0075290BEF2E1975178A182103B9ACA00A120C2008E32102782104ED14B6558076D72708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB0093303535E2F82381012CA0F0024477F0091045103412F823F00BE05F041501F03502FA4021F001FA40D20031FA0082103B9ACA001DA121945314A0A1DE22D70B01C300209205A19135E220C2FFF2E192218E3E821005138D91C8500BCF16500DCF1671244B145448C0708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB00106994102C395BE20114008A8E3528F0018210D53276DB103946096D71708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB0093383430E21045103412F823F00B009A32353582102FCB26A2BA8E3A7082108B77173504C8CBFF5005CF161443308040708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB00E05F04840FF2F00093083001258C2040FA201938083001658C20407D200CB8083001A58C204064200A38083001E58C20404B2007B8083002258C204032200538083002650C20191EB83002A4E00C9D781E9C600069006AC0BC018060840EE6B2802A0060840EE6B2802A00A08418B9101A68608209E3402A410830856456F81B04A5A9D6A0192A4139200201201A1B0201201C1D0021081BA50C1B5C0838343E903E8034CFCC200017321400F3C5807E80B2CFF26000513B513434FFFE900835D2708027DFC07E9035353D0134CFCC0415C415B80C1C1B5B5B5B490415C415A0002B01B232FFD40173C59400F3C5B3333D0032CFF27B5520020120202102012024250013BBB39F00A175F07F008802027422230010A874F00A10475F07000CA959F00A6C71000DB8FCFF00A5F03802012026270013B64A5E014204EBE0FA1000C7B461843AE9240F152118001E5C08DE014206EBE0FA1A60E038001E5C339E8086007AE140F8001E5C33B84111C466105E033E04883DCB11FB64DDC4964AD1BA06B879240DC23572F37CC5CAAAB143A2FFFBC4180012660F003C003060FE81EDF4260F00306EB1583C";
        String dnsCollectionCodeHex = "B5EE9C7241021D010002C7000114FF00F4A413F4BCF2C80B0102016202030202CC040502012017180201200607020120131402012008090201200D0E016D420C70094840FF2F0DE01D0D3030171B0925F03E0FA403001D31FED44D0D4D4303122C000E30210245F048210370FEC51BADC840FF2F080A0201200B0C00D032F82320821062E44069BCF2E0C701F00420D74920C218F2E0C8208103F0BBF2E0C92078A908C000F2E0CA21F005F2E0CB58F00714BEF2E0CC22F9018050F833206EB38E10D0F4043052108307F40E6FA131F2D0CD9130E2C85004CF16C9C85003CF1612CCC9F00C000D1C3232C072742000331C27C074C1C07000082CE500A98200B784B98C4830003CB432600201200F100201201112004F3223880875D244B5C61673C58875D2883000082CE6C070007CB83280B50C3400A44C78B98C727420007F1C0875D2638D572E882CE38B8C00B4C1C8700B48F0802C0929BE14902E6C08B08BC8F04EAC2C48B09800F05EC4EC04AC6CC82CE500A98200B784F7B99B04AEA00093083001258C2040FA201938083001658C20407D200CB8083001A58C204064200A38083001E58C20404B2007B8083002258C204032200538083002650C20191EB83002A4E00C9D781E9C600069006AC0BC018060840EE6B2802A0060840EE6B2802A00A08418B9101A68608209E3402A410830856456F81B04A5A9D6A0192A41392002015815160039D2CF8053810F805BBC00C646582AC678B387D0165B5E66664C0207D804002D007232FFFE0A33C5B25C083232C044FD003D0032C03260001B3E401D3232C084B281F2FFF27420020120191A0201201B1C0007B8B5D318001FBA7A3ED44D0D4D43031F00A7001F00B8001BB905BED44D0D4D430307FF002128009DBA30C3020D74978A908C000F2E04620D70A07C00021D749C0085210B0935B786DE0209501D3073101DE21F0035122D71830F9018200BA93C8CB0F01820167A3ED43D8CF16C90191789170E212A0018F83DF327";

        Options optionsDnsCollection = Options.builder()
                .collectionContent(NftUtils.createOffchainUriCell("https://dns.ton.org/collection.json"))
                .dnsItemCodeHex(dnsItemCodeHex)
                .code(Cell.fromBoc(dnsCollectionCodeHex))
                .build();

        Wallet dnsCollectionWallet = new Wallet(WalletVersion.dnsCollection, optionsDnsCollection);
        DnsCollection dnsCollection = dnsCollectionWallet.create();
        log.info("DNS collection address {}", dnsCollection.getAddress().toString(true, true, true)); // EQDZ3EPcM7LgboM68eyfNoqjM365gB2Edj4DtLxfObt-u4Cy

        dnsCollection.deploy(tonlib, adminWallet, Utils.toNano(0.5), adminKeyPair1);

        Utils.sleep(15);

        getDnsCollectionInfo(dnsCollection);

        // create and assign DNS item to DNS collection
//        CellBuilder dnsItemCell = CellBuilder.beginCell();
//        dnsItemCell.storeString("testDnsItem");
//        String dnsItemIndex = Utils.bytesToHex(dnsItemCell.hash());
//        Options optionsDnsItem = Options.builder()
//                .index(dnsItemIndex)
//                .collectionAddress(dnsCollection.getAddress())
//                .build();
//
//        Wallet dnsItemWallet = new Wallet(WalletVersion.dnsItem, optionsDnsItem);
//        DnsItem dnsItem = dnsItemWallet.create();
//        log.info("dns item address {}", dnsItem.getAddress().toString(true, true, true));

        Address dnsItemAddress = Address.of("EQDEwJoXDBqBFI-0pvYJOuI5MEuvYthhwkl56qi5oclHMyGC"); // EQBZY9r6QJ23rNi6KJBtApZjmt5Qj-wLABQHpRhtjpmpcNsG
        log.info("dns item address {}", dnsItemAddress.toString(true, true, true));

        Options optionsDnsItem = Options.builder()
                .address(dnsItemAddress)
                .build();
//
        DnsItem dnsItem = new DnsItem(optionsDnsItem);

        getDnsItemInfo(dnsCollection, dnsItem);

        setWalletRecord(dnsItem);

        transferDnsItem(dnsItem);

        releaseDnsItem(dnsItem);

        govDnsItem(dnsItem);

        getStaticData(dnsItem);
    }

    private void getDnsCollectionInfo(DnsCollection dnsCollection) {
        CollectionData data = dnsCollection.getCollectionData(tonlib);
        log.info("dns collection info {}", data);
        log.info("dns collection owner {}", data.getOwnerAddress());

        CellBuilder cell = CellBuilder.beginCell();
        cell.storeString("alicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicea");
        String hash = Utils.bytesToHex(cell.hash());
        log.info("hash {}", hash); // 8b98cad1bf9de7e1bd830ba3fba9608e6190825dddcf7edac7851ee16a692e81

        Address apple = dnsCollection.getNftItemAddressByIndex(tonlib, new BigInteger("6f0d5e27c981d01ba43aaf3d346a84beb1e8757b988465a325b3217ec3257af6", 16)); // "apple"
        Address alice3 = dnsCollection.getNftItemAddressByIndex(tonlib, new BigInteger("b413c774757e648677382bd685a02242640b846f9dc92455a2a044b9c3279d5a", 16)); // "alice-alice-alice"
        Address aliceX = dnsCollection.getNftItemAddressByIndex(tonlib, new BigInteger(hash, 16)); // "alicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicealicea"

        log.info("addressByIndex = {}", apple.toString(true, true, true));
        log.info("addressByIndex = {}", alice3.toString(true, true, true));
        log.info("addressByIndex = {}", aliceX.toString(true, true, true));

        Address alice3Resolved = (Address) dnsCollection.resolve(tonlib, "alice-alice-alice", DNS_CATEGORY_NEXT_RESOLVER, true);
        log.info("alice-alice-alice resolved to {}", alice3Resolved.toString(true, true, true));
    }

    private void getDnsItemInfo(DnsCollection dnsCollection, DnsItem dnsItem) {
        ItemData data = dnsCollection.getNftItemContent(tonlib, dnsItem);
        log.info("dns item data {}", data);
        log.info("dns item collection address {}", data.getCollectionAddress().toString(true, true, true));
        log.info("dns item owner address {}", data.getOwnerAddress().toString(true, true, true));

//        if (nonNull(data.getOwnerAddress())) { // cannot get auction info - result [], 0, 0
//            AuctionInfo auctionInfo = dnsItem.getAuctionInfo(tonlib);
//            Address maxBidAddress = auctionInfo.getMaxBidAddress();
//            BigInteger maxBidAmount = auctionInfo.getMaxBidAmount();
//            log.info("auction info {}", auctionInfo);
//        }

        String domain = dnsItem.getDomain(tonlib);
        log.info("domain {}", domain);

        long lastFillUpTime = dnsItem.getLastFillUpTime(tonlib);
        log.info("lastFillUpTime {}, {}", lastFillUpTime, Utils.toUTC(lastFillUpTime));

        //dnsItem.resolve(tonlib, ".");
        //dnsItem.resolve(tonlib, ".", DNS_CATEGORY_NEXT_RESOLVER, true);
        //dnsItem.resolve(tonlib, ".", DNS_CATEGORY_WALLET, true);
    }

    private void setWalletRecord(DnsItem dnsItem) {
        long seqno = adminWallet.getSeqno(tonlib);

        ExternalMessage extMsg = adminWallet.createTransferMessage(
                adminKeyPair1.getSecretKey(),
                dnsItem.getAddress(), // toAddress
                Utils.toNano(0.1),
                seqno,
                DnsItem.createChangeContentEntryBody(DNS_CATEGORY_WALLET,
                        DnsUtils.createSmartContractAddressRecord(Address.of("EQA0i8-CdGnF_DhUHHf92R1ONH6sIA9vLZ_WLcCIhfBBXwtG")),
                        0));

        tonlib.sendRawMessage(Utils.bytesToBase64(extMsg.message.toBoc(false)));
    }

    private void transferDnsItem(DnsItem dnsItem) {
        long seqno = adminWallet.getSeqno(tonlib);

        ExternalMessage extMsg = adminWallet.createTransferMessage(
                adminKeyPair1.getSecretKey(),
                dnsItem.getAddress(), // toAddress
                Utils.toNano(0.05),
                seqno,
                dnsItem.createTransferBody(
                        0,
                        Address.of("EQBs7JfxnH2jNAlo0ytfKc77sSHQsUBzofngOkcLZyJlmm3j"),
                        Utils.toNano(0.02),
                        "gift".getBytes(),
                        adminWallet.getAddress()));

        tonlib.sendRawMessage(Utils.bytesToBase64(extMsg.message.toBoc(false)));
    }

    private void releaseDnsItem(DnsItem dnsItem) {
        long seqno = adminWallet.getSeqno(tonlib);

        CellBuilder payload = CellBuilder.beginCell();
        payload.storeUint(0x4ed14b65, 32); // op::dns_balance_release = 0x4ed14b65;
        payload.storeUint(123, 6);

        ExternalMessage extMsg = adminWallet.createTransferMessage(
                adminKeyPair1.getSecretKey(),
                dnsItem.getAddress(), // toAddress
                Utils.toNano(5),
                seqno,
                payload.endCell());

        tonlib.sendRawMessage(Utils.bytesToBase64(extMsg.message.toBoc(false)));
    }

    private void govDnsItem(DnsItem dnsItem) {
        long seqno = adminWallet.getSeqno(tonlib);

        CellBuilder payload = CellBuilder.beginCell();
        payload.storeUint(0x44beae41, 32); // op::process_governance_decision = 0x44beae41;
        payload.storeUint(123, 6);

        ExternalMessage extMsg = adminWallet.createTransferMessage(
                adminKeyPair1.getSecretKey(),
                dnsItem.getAddress(), // toAddress
                Utils.toNano(1),
                seqno,
                payload.endCell());

        tonlib.sendRawMessage(Utils.bytesToBase64(extMsg.message.toBoc(false)));
    }

    private void getStaticData(DnsItem dnsItem) {
        long seqno = adminWallet.getSeqno(tonlib);

        ExternalMessage extMsg = adminWallet.createTransferMessage(
                adminKeyPair1.getSecretKey(),
                dnsItem.getAddress(), // toAddress
                Utils.toNano(0.05),
                seqno,
                dnsItem.createStaticDataBody(661));

        tonlib.sendRawMessage(Utils.bytesToBase64(extMsg.message.toBoc(false)));
    }

    // deployRootDns();
    // dnsResolve();
//    deployDnsCollection();
//    getDnsCollectionInfo
    // setWalletRecord();
    // transferDnsItem();
    // releaseDnsItem();
    // govDnsItem();
    // getStaticData();

}