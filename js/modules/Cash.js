// Constants
import {
    BIRTH_TIME,
    VALID,
    ISSUER,
    OWNER,
    VALUE,
    CD,
    CDD,
    ID,
    CASH_ID,
    LAST_TIME,
    SPEND_TIME
} from '../constants/fieldNames.js';
import {
    DEFAULT_ID_LENGTH,
    DEFAULT_TIME_LENGTH,
    DEFAULT_CD_LENGTH,
    DEFAULT_BOOLEAN_LENGTH,
    DEFAULT_AMOUNT_LENGTH
} from '../constants/constants.js';

const COINBASE = 'coinbase';
const OneDayInterval = 24 * 60 * 60;

class Cash {
    constructor() {
        // Calculated
        this.id = null;
        this.valid = null; // Is this cash valid (utxo), or spent (stxo)
        this.issuer = null; // first input fid when this cash was born
        this.value = null; // in satoshi
        this.lastTime = null;
        this.lastHeight = null;
        
        // From utxo
        this.birthIndex = null; // index of cash. Order in cashs of the tx when created
        this.type = null; // type of the script. P2PKH,P2SH,OP_RETURN,Unknown,MultiSig
        this.owner = null; // address
        this.lockScript = null; // LockScript
        this.birthTxId = null; // txid, hash in which this cash was created
        this.birthTxIndex = null; // Order in the block of the tx in which this cash was created
        this.birthBlockId = null; // block ID, hash of block head
        this.birthTime = null; // Block time when this cash is created
        this.birthHeight = null; // Block height

        // From input
        this.spendTime = null; // Block time when spent
        this.spendTxId = null; // Tx hash when spent
        this.spendHeight = null; // Block height when spent
        this.spendTxIndex = null; // Order in the block of the tx in which this cash was spent
        this.spendBlockId = null; // block ID, hash of block head
        this.spendIndex = null; // Order in inputs of the tx when spent
        this.unlockScript = null; // unlock script
        this.sigHash = null; // sigHash
        this.sequence = null; // nSequence
        this.cdd = null; // CoinDays Destroyed
        this.cd = null; // CoinDays

    }

    static getFieldWidthMap() {
        return {
            [OWNER]: DEFAULT_ID_LENGTH,
            [VALID]: DEFAULT_BOOLEAN_LENGTH,
            [VALUE]: DEFAULT_AMOUNT_LENGTH,
            [LAST_TIME]: DEFAULT_TIME_LENGTH,
            [CDD]: DEFAULT_CD_LENGTH,
            [ID]: DEFAULT_ID_LENGTH
        };
    }

    static getTimestampFieldList() {
        return [BIRTH_TIME, LAST_TIME, SPEND_TIME];
    }

    static getSatoshiFieldList() {
        return [VALUE];
    }

    static getHeightToTimeFieldMap() {
        return {};
    }

    static getShowFieldNameAsMap() {
        const currentLang = window.currentLanguage || 'en';
        const fieldNames = window.strings?.[currentLang]?.fieldNames || {};
        
        return {
            [ID]: fieldNames.id || CASH_ID,
            [OWNER]: fieldNames.owner || 'Owner',
            [VALID]: fieldNames.valid || 'Valid',
            [VALUE]: fieldNames.value || 'Value',
            [LAST_TIME]: fieldNames.lastTime || 'Last Time',
            [CDD]: fieldNames.cdd || 'CDD',
            [BIRTH_TIME]: fieldNames.birthTime || 'Birth Time',
            [ISSUER]: fieldNames.issuer || 'Issuer'
        };
    }

    static getReplaceWithMeFieldList() {
        return [OWNER, ISSUER];
    }

    static getInputFieldDefaultValueMap() {
        return {};
    }

    //Static Cash methods
    static makeCashId(b36PreTxIdAndIndex) {
        // For browser environment, we'll use a simple hash function
        let hash = 0;
        for (let i = 0; i < b36PreTxIdAndIndex.length; i++) {
            const char = b36PreTxIdAndIndex.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return hash.toString(16);
    }

    static makeCashIdFromTxIdAndIndex(txId, j) {
        if (!txId || j === null) return null;
        return Cash.makeCashId(txId + j.toString());
    }

    static makeCashListForPay(cashList) {
        if (!cashList || cashList.length === 0) return [];
        
        return cashList.map(cash => {
            const newCash = new Cash();
            newCash.birthTxId = cash.birthTxId;
            newCash.birthIndex = cash.birthIndex;
            newCash.value = cash.value;
            return newCash;
        });
    }

    makeId(txId, index) {
        this.id = Cash.makeCashIdFromTxIdAndIndex(txId, index);
        return this.id;
    }

    toJson() {
        return JSON.stringify(this);
    }

    static fromJson(json) {
        return Object.assign(new Cash(), JSON.parse(json));
    }

    toBytes() {
        return new TextEncoder().encode(this.toJson());
    }

    static fromBytes(bytes) {
        return Cash.fromJson(new TextDecoder().decode(bytes));
    }

    static fromUtxo(utxo) {
        const cash = new Cash();
        cash.birthTxId = utxo.txid;
        cash.birthIndex = utxo.vout;
        cash.owner = utxo.address;
        cash.lockScript = utxo.scriptPubKey;
        cash.value = utxo.amount * 100000000; // Convert to satoshi
        cash.valid = true;
        return cash;
    }

    static fromUtxoList(utxoList) {
        if (!utxoList || utxoList.length === 0) return [];
        return utxoList.map(Cash.fromUtxo);
    }

    static sumCashValue(cashList) {
        if (!cashList || cashList.length === 0) return 0;
        return cashList.reduce((sum, cash) => sum + (cash.value || 0), 0);
    }

    static sumCashAmount(cashList) {
        if (!cashList || cashList.length === 0) return 0;
        const sum = Cash.sumCashValue(cashList);
        return sum / 100000000; // Convert from satoshi to coin
    }

    static sumCashCd(cashList) {
        if (!cashList || cashList.length === 0) return 0;
        return cashList.reduce((sum, cash) => {
            const cd = cash.makeCd();
            return sum + (cd || 0);
        }, 0);
    }

    static checkImmatureCoinbase(cashList, bestHeight) {
        return cashList.filter(cash => 
            !(cash.issuer === COINBASE && bestHeight !== 0 && 
              (bestHeight - cash.birthHeight) < OneDayInterval * 10)
        );
    }

    makeCd() {
        if (!this.value || !this.birthTime) return null;
        const now = Math.floor(Date.now() / 1000);
        const age = now - this.birthTime;
        this.cd = (this.value * age) / (24 * 60 * 60);
        return this.cd;
    }
}

export default Cash; 