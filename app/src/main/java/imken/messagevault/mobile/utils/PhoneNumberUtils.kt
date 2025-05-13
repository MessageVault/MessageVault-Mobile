package imken.messagevault.mobile.utils

import timber.log.Timber

/**
 * 电话号码工具类
 *
 * 提供电话号码格式化和标准化的功能，用于确保不同格式的电话号码能够正确匹配
 */
object PhoneNumberUtils {
    
    /**
     * 标准化电话号码，移除所有非数字字符
     * 
     * @param phoneNumber 原始电话号码
     * @return 标准化后的电话号码，只包含数字
     */
    fun normalizePhoneNumber(phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) {
            return ""
        }
        
        // 移除所有非数字字符，包括空格、括号、破折号等
        return phoneNumber.replace(Regex("[^\\d+]"), "")
    }
    
    /**
     * 比较两个电话号码是否匹配
     * 
     * @param phone1 第一个电话号码
     * @param phone2 第二个电话号码
     * @return 如果两个号码匹配则返回true
     */
    fun phoneNumbersMatch(phone1: String?, phone2: String?): Boolean {
        // 如果任一号码为空，则不匹配
        if (phone1.isNullOrBlank() || phone2.isNullOrBlank()) {
            return false
        }
        
        val normalized1 = normalizePhoneNumber(phone1)
        val normalized2 = normalizePhoneNumber(phone2)
        
        // 直接比较标准化后的号码
        if (normalized1 == normalized2) {
            return true
        }
        
        // 考虑国家代码和区号的情况，检查是否一个号码是另一个的后缀
        if (normalized1.length > normalized2.length && normalized1.endsWith(normalized2)) {
            return true
        }
        
        if (normalized2.length > normalized1.length && normalized2.endsWith(normalized1)) {
            return true
        }
        
        // 只比较最后几位，通常对于国内号码，最后8-9位决定了唯一性
        val minCompareLength = 8
        if (normalized1.length >= minCompareLength && normalized2.length >= minCompareLength) {
            val suffix1 = normalized1.takeLast(minCompareLength)
            val suffix2 = normalized2.takeLast(minCompareLength)
            return suffix1 == suffix2
        }
        
        return false
    }
    
    /**
     * 获取可能的电话号码变体，用于提高匹配率
     * 
     * @param phoneNumber 原始电话号码
     * @return 可能的电话号码变体列表
     */
    fun getPossibleNumberVariants(phoneNumber: String?): List<String> {
        if (phoneNumber.isNullOrBlank()) {
            return emptyList()
        }
        
        val normalized = normalizePhoneNumber(phoneNumber)
        val variants = mutableListOf<String>()
        
        // 添加原始标准化号码
        variants.add(normalized)
        
        // 如果以+开头，添加不带+的版本
        if (normalized.startsWith("+")) {
            variants.add(normalized.substring(1))
        }
        
        // 处理中国号码的常见格式变化
        if (normalized.startsWith("+86")) {
            variants.add(normalized.substring(3)) // 去掉国家代码+86
        } else if (normalized.startsWith("86")) {
            variants.add(normalized.substring(2)) // 去掉国家代码86
        }
        
        // 处理带区号的号码，比如0xx-xxxxxxxx
        if (normalized.length > 10 && normalized.startsWith("0")) {
            variants.add(normalized.substring(1)) // 去掉开头的0
        }
        
        Timber.d("[Mobile] DEBUG [PhoneUtils] 为号码 $phoneNumber 生成的变体: $variants")
        
        return variants
    }
} 