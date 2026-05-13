/**
 * OCR 坐标工具集
 *
 * Node.js 环境使用：
 *   const { toAbsolute, clickByCenter, findByText, sortByY } = require('./ocr-utils.js');
 *
 * ES Module 环境使用：
 *   import { toAbsolute, clickByCenter, findByText, sortByY } from './ocr-utils.js';
 */

/**
 * 将 OCR 返回的相对坐标转换为屏幕绝对坐标
 * @param {Array}  ocrResults - OCR 返回的 data 数组（相对坐标）
 * @param {Object} offset     - 截图在屏幕中的偏移 { x: number, y: number }
 * @returns {Array} - 转换后的结果数组，每个元素增加 absoluteCenter / absoluteBox 字段
 */
function toAbsolute(ocrResults, offset) {
    return ocrResults.map(r => {
        const absoluteCenter = {
            x: r.centerX + offset.x,
            y: r.centerY + offset.y
        };
        const absoluteBox = r.box.map(p => ({
            x: p.x + offset.x,
            y: p.y + offset.y
        }));
        return {
            ...r,
            absoluteCenter,
            absoluteBox
        };
    });
}

/**
 * 根据文字中心坐标执行点击
 * @param {Object} center  - { x: number, y: number }，屏幕绝对坐标
 * @param {string} method  - 点击方式：'adb' | 'appium' | 'autojs'
 * @param {Object} options - 各方式的额外参数
 *
 * method='adb':     options = { serial: 'emulator-5554' }  // 可选
 * method='appium':  options = { driver: webDriver }
 * method='autojs':  options = {}  // 在 AutoJS 环境中直接调用 click(x, y)
 *
 * @returns {Promise<void>}
 */
async function clickByCenter(center, method = 'adb', options = {}) {
    const { x, y } = center;

    switch (method) {
        case 'adb': {
            const serial = options.serial ? `-s ${options.serial}` : '';
            await execAsync(`adb ${serial} shell input tap ${Math.round(x)} ${Math.round(y)}`);
            break;
        }
        case 'appium': {
            await options.driver.touchAction({
                action: 'tap',
                x: Math.round(x),
                y: Math.round(y)
            });
            break;
        }
        case 'autojs': {
            if (typeof click === 'function') {
                click(Math.round(x), Math.round(y));
            } else {
                console.warn('AutoJS click() 不可用，请确保在 AutoJS 环境中运行');
            }
            break;
        }
        default:
            throw new Error(`未知的点击方式: ${method}`);
    }
}

/**
 * 在文字区域执行长按（取区域中心点）
 * @param {Object} box     - 4个角点数组 [{x,y}, {x,y}, {x,y}, {x,y}]
 * @param {number} ms      - 长按毫秒数，默认 500ms
 * @param {string} method   - 同 clickByCenter
 * @param {Object} options  - 同 clickByCenter
 */
async function longPressByBox(box, ms = 500, method = 'adb', options = {}) {
    const cx = box.reduce((s, p) => s + p.x, 0) / box.length;
    const cy = box.reduce((s, p) => s + p.y, 0) / box.length;

    switch (method) {
        case 'adb': {
            const serial = options.serial ? `-s ${options.serial}` : '';
            await execAsync(`adb ${serial} shell input swipe ${Math.round(cx)} ${Math.round(cy)} ${Math.round(cx)} ${Math.round(cy)} ${ms}`);
            break;
        }
        case 'appium': {
            await options.driver.touchAction({
                action: 'longPress',
                x: Math.round(cx),
                y: Math.round(cy),
                duration: ms
            });
            break;
        }
        default:
            throw new Error(`未知的点击方式: ${method}`);
    }
}

/**
 * 查找包含指定文字的结果
 * @param {Array}  ocrResults - OCR 结果数组
 * @param {string} text      - 要查找的文字（支持模糊匹配）
 * @param {boolean} exact     - 是否精确匹配，默认 false
 * @returns {Array}
 */
function findByText(ocrResults, text, exact = false) {
    if (exact) {
        return ocrResults.filter(r => r.text === text);
    }
    const lower = text.toLowerCase();
    return ocrResults.filter(r => r.text.toLowerCase().includes(lower));
}

/**
 * 按 Y 坐标排序（从上到下）
 * @param {Array} ocrResults - OCR 结果数组
 * @returns {Array}
 */
function sortByY(ocrResults) {
    return [...ocrResults].sort((a, b) => {
        const ay = (a.absoluteCenter || a).centerY;
        const by = (b.absoluteCenter || b).centerY;
        return ay - by;
    });
}

/**
 * 生成盒子的绘图日志（用于调试可视化）
 * @param {Array}  ocrResults - OCR 结果数组
 * @param {string} color      - 边框颜色，默认 'red'
 * @param {number} lineWidth  - 线宽，默认 2
 * @returns {string[]} Canvas 绘图指令日志
 */
function debugDrawBoxes(ocrResults, color = 'red', lineWidth = 2) {
    return ocrResults.map((r, i) => {
        const box = r.absoluteBox || r.box;
        const points = box.map(p => `[${Math.round(p.x)},${Math.round(p.y)}]`).join(' -> ');
        return `#${i} "${r.text}" 框: ${points}`;
    });
}

// Node.js exec 封装
function execAsync(cmd) {
    return new Promise((resolve, reject) => {
        const { exec } = require('child_process');
        exec(cmd, (err, stdout, stderr) => {
            if (err) reject(err);
            else resolve(stdout);
        });
    });
}

module.exports = {
    toAbsolute,
    clickByCenter,
    longPressByBox,
    findByText,
    sortByY,
    debugDrawBoxes
};
