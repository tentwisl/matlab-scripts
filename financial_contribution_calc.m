%contribution.m

function totalContribution = contribution(salary, percent)
    if nargin < 2
        prompt = {'annual salary:', 'contribution percentage:'};
        dlgtitle = 'plan Input';
        dims = [1 50];
        definput = {'50000','10'};  
        answer = inputdlg(prompt, dlgtitle, dims, definput);
        if isempty(answer)
            error('No input provided.');
        end
        salary = str2double(answer{1});
        percent = str2double(answer{2});
    end

   %error check
    if isnan(salary) || salary < 0
        error('salary must be a positive value.');
    end
    if isnan(percent) || percent < 0 || percent > 100
        error('contribution percentage must be between 0 and 100.');
    end

   %salary checks
    if salary <= 30000
        allowedEmployee = 0.10 * salary;
        companyMatch = 0.10 * salary;
    elseif salary <= 60000
        allowedEmployee = 0.10 * 30000 + 0.05 * (salary - 30000);
        companyMatch = 0.10 * 30000 + 0.05 * (salary - 30000);
    elseif salary <= 100000
        allowedEmployee = 0.10 * 60000 + 0.08 * (salary - 60000);
        companyMatch = 0.10 * 30000 + 0.05 * (60000 - 30000);
    else 
        allowedEmployee = 0.10 * 60000 + 0.08 * (100000 - 60000);
        companyMatch = 0;  
    end

    chosenContribution = salary * (percent / 100);
    employeeContribution = min(chosenContribution, allowedEmployee);
    totalContribution = employeeContribution + companyMatch;

    %financial summary
    fprintf('\n--- Financial Summary ---\n');
    fprintf('annual salary: $%.2f\n', salary);
    fprintf('contribution percentage chosen: %.2f%%\n', percent);
    fprintf('employee contribution: $%.2f\n', employeeContribution);
end
