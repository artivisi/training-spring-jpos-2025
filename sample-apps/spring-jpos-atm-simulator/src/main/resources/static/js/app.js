document.addEventListener('DOMContentLoaded', () => {
    const typeSelect = document.getElementById('type');
    const amountField = document.getElementById('amountField');
    const atmForm = document.getElementById('atmForm');

    typeSelect.addEventListener('change', (e) => {
        if (e.target.value === 'WITHDRAWAL') {
            amountField.classList.remove('hidden');
            document.getElementById('amount').required = true;
        } else {
            amountField.classList.add('hidden');
            document.getElementById('amount').required = false;
        }
    });

    atmForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const formData = {
            pan: document.getElementById('pan').value,
            accountNumber: document.getElementById('accountNumber').value,
            pin: document.getElementById('pin').value,
            type: document.getElementById('type').value,
            amount: document.getElementById('type').value === 'WITHDRAWAL' ?
                    parseFloat(document.getElementById('amount').value) : null,
            terminalId: 'ATM00001'
        };

        const resultDiv = document.getElementById('result');
        const progressDiv = document.getElementById('progressIndicator');
        const progressText = document.getElementById('progressText');
        const submitButton = atmForm.querySelector('button[type="submit"]');

        // Hide previous result and show progress
        resultDiv.classList.add('hidden');
        progressDiv.classList.remove('hidden');
        submitButton.disabled = true;
        submitButton.classList.add('opacity-50', 'cursor-not-allowed');

        // Update progress text based on transaction type
        const transactionType = formData.type === 'BALANCE' ? 'balance inquiry' : 'withdrawal';
        progressText.textContent = `Processing ${transactionType}...`;

        const startTime = new Date();

        try {
            const response = await fetch('/atm/transaction', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });

            const data = await response.json();
            const endTime = new Date();
            const duration = ((endTime - startTime) / 1000).toFixed(2);
            const timestamp = endTime.toLocaleTimeString();

            // Hide progress indicator
            progressDiv.classList.add('hidden');

            if (data.responseCode === '00') {
                resultDiv.className = 'mb-4 p-4 rounded-lg bg-green-100 border-2 border-green-400 text-green-800 animate-fade-in';
                resultDiv.innerHTML = `
                    <div class="flex items-start mb-2">
                        <svg class="h-6 w-6 text-green-600 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                        </svg>
                        <div class="flex-1">
                            <p class="font-bold text-lg">${data.responseMessage}</p>
                            <p class="text-2xl font-bold mt-2">Balance: $${data.balance}</p>
                            ${data.amount ? `<p class="mt-1">Withdrawn: $${data.amount}</p>` : ''}
                        </div>
                    </div>
                    <div class="mt-3 pt-3 border-t border-green-300 text-sm">
                        <p><span class="font-medium">Time:</span> ${timestamp}</p>
                        <p><span class="font-medium">Duration:</span> ${duration}s</p>
                        <p><span class="font-medium">Response Code:</span> ${data.responseCode}</p>
                    </div>
                `;
            } else {
                resultDiv.className = 'mb-4 p-4 rounded-lg bg-red-100 border-2 border-red-400 text-red-800 animate-fade-in';
                resultDiv.innerHTML = `
                    <div class="flex items-start mb-2">
                        <svg class="h-6 w-6 text-red-600 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                        </svg>
                        <div class="flex-1">
                            <p class="font-bold text-lg">Transaction Failed</p>
                            <p class="mt-1">${data.responseMessage}</p>
                        </div>
                    </div>
                    <div class="mt-3 pt-3 border-t border-red-300 text-sm">
                        <p><span class="font-medium">Time:</span> ${timestamp}</p>
                        <p><span class="font-medium">Duration:</span> ${duration}s</p>
                        <p><span class="font-medium">Response Code:</span> ${data.responseCode}</p>
                    </div>
                `;
            }

            resultDiv.classList.remove('hidden');

            // Scroll result into view
            resultDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

        } catch (error) {
            const endTime = new Date();
            const duration = ((endTime - startTime) / 1000).toFixed(2);
            const timestamp = endTime.toLocaleTimeString();

            progressDiv.classList.add('hidden');
            resultDiv.className = 'mb-4 p-4 rounded-lg bg-red-100 border-2 border-red-400 text-red-800 animate-fade-in';
            resultDiv.innerHTML = `
                <div class="flex items-start mb-2">
                    <svg class="h-6 w-6 text-red-600 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                    </svg>
                    <div class="flex-1">
                        <p class="font-bold text-lg">Connection Error</p>
                        <p class="mt-1">${error.message}</p>
                    </div>
                </div>
                <div class="mt-3 pt-3 border-t border-red-300 text-sm">
                    <p><span class="font-medium">Time:</span> ${timestamp}</p>
                    <p><span class="font-medium">Duration:</span> ${duration}s</p>
                </div>
            `;
            resultDiv.classList.remove('hidden');
        } finally {
            // Re-enable submit button
            submitButton.disabled = false;
            submitButton.classList.remove('opacity-50', 'cursor-not-allowed');
        }
    });
});
