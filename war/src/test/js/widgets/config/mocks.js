import { getWindow } from 'window-handle';
import localStorage from '../../../../main/js/util/localStorage';

// mock the behaviors stuff.
export function mockBehaviorShim() {
    const mockActualBehaviorShim = jest.requireActual('../../../../main/js/util/behavior-shim');

    jest.mock('../../../../main/js/util/behavior-shim', () => ({
        __esModule: true,
        default: {
            ...mockActualBehaviorShim.default,
            specify: jest.fn((selector, id, priority, behavior) => behavior())
        }
    }));
}

// Mock out the Event.fire function
global.Event = { // eslint-disable-line no-undef
    fire: jest.fn()
};

getWindow(function() {
    localStorage.setMock();
});
