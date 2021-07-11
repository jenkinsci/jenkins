(function(){
    const togglePasswordType = function(event) {
        const passwordField = document.getElementById('password1');
        const showPass = document.getElementById('showPassword');
        if (showPass.checked) {
            passwordField.type = 'text';
        } else {
            passwordField.type = 'password';
        }
    };

    const passwordScore = function(password) {
        var score = 0;
        if (!password) {
            return score
        }
        // award every unique letter until 5 repetitions
        var letters = {};
        for (var i = 0; i < password.length; i++) {
            letters[password[i]] = (letters[password[i]] || 0) + 1
            score += 5.0 / letters[password[i]]
        }

        // bonus points for mixing it up
        var variations = {
            digits: /\d/.test(password),
            lower: /[a-z]/.test(password),
            upper: /[A-Z]/.test(password),
            nonWords: /\W/.test(password)
        };

        var variationCount = 0;
        for (var check in variations) {
            variationCount += variations[check] === true ? 1 : 0;
        }
        score += (variationCount - 1) * 10;
        return parseInt(score);
    };

    const passwordStrength = function(score) {
        var dataSource = document.getElementById('passwordStrengthWrapper');
        if (score > 80) {
            return dataSource.getAttribute('data-strong');
        }
        if (score > 60) {
            return dataSource.getAttribute('data-moderate');
        }
        if (score >= 30) {
            return dataSource.getAttribute('data-weak');
            return "${%Weak}";
        }
        return dataSource.getAttribute('data-poor');
    };

    const passwordStrengthColor = function(score) {
        if (score > 80) {
            return "#3a7911"
        }
        if (score > 60) {
            return "#c6810e"
        }
        if (score >= 30) {
            return "#de5912"
        }
        return "#c4000a"
    };

    const validatePassword = function() {
        const password = document.getElementById('password1');
        if (password) {
            const passwordStrengthWrapper =
                    document.getElementById('passwordStrengthWrapper');
            passwordStrengthWrapper.hidden = false;
            const score = passwordScore(password.value);
            const passwordStrengthIndicator = document.getElementById('passwordStrength');
            passwordStrengthIndicator.innerText = passwordStrength(score);
            passwordStrengthIndicator.setAttribute('style', 'color: ' +
                passwordStrengthColor(score));
            document.getElementById('password2').value = password.value;
        }
    };

    document.getElementById('password1').onkeyup = validatePassword;
    document.getElementById('showPassword').onclick = togglePasswordType;


    if (document.getElementById('passwordStrengthWrapper')) {
        document.getElementById('passwordStrengthWrapper').hidden = true;
    }
})();
